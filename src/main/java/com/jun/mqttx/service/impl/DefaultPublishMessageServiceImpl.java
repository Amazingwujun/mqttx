/*
 * Copyright 2020-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IPublishMessageService;
import com.jun.mqttx.utils.Serializer;
import com.jun.mqttx.utils.Uuids;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * publish message store by redis.
 *
 * @author Jun
 * @since 1.0.4
 */
@Slf4j
@Service
public class DefaultPublishMessageServiceImpl implements IPublishMessageService, Runnable {
    //@formatter:off

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId EAST_8 = ZoneOffset.ofHours(8);
    private static final int REDIS_SCAN_COUNT = 10;
    private final ReactiveRedisTemplate<String, byte[]> redisTemplate;
    private final ReactiveStringRedisTemplate stringRedisTemplate;
    private final Serializer serializer;
    private final String pubMsgSetPrefix;

    /**
     * key: {@link PubMsg#uniqueId()}
     * value: 报文内容
     */
    private final Map<String, byte[]> uniqueIdAndPayloadMap = new ConcurrentHashMap<>();
    private final Map<String, String> clientIdMessageIdAndUniqueIdMap = new ConcurrentHashMap<>();
    /** 当 pub msg 阈值大于指定值时，报文采用二级寻址方式处理 */
    private final int thresholdInMessage;
    /** 共享载荷存储 key prefix */
    private final String sharablePayloadKeyPrefix;
    /** 共享载荷关联的客户端 id 列表 */
    private final String uniqueIdClientIdsSetPrefix;
    private final Duration payloadCleanWorkInterval;
    private final String brokerId;

    //@formatter:on

    public DefaultPublishMessageServiceImpl(ReactiveRedisTemplate<String, byte[]> redisTemplate,
                                            ReactiveStringRedisTemplate stringRedisTemplate,
                                            Serializer serializer,
                                            MqttxConfig mqttxConfig) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.serializer = serializer;
        this.brokerId = mqttxConfig.getBrokerId();
        var redisKey = mqttxConfig.getRedis();
        this.pubMsgSetPrefix = redisKey.getPubMsgSetPrefix();

        var sharableConfig = mqttxConfig.getSharablePayload();
        this.sharablePayloadKeyPrefix = sharableConfig.getPayloadKeyPrefix();
        this.thresholdInMessage = sharableConfig.getThresholdInMessage();
        this.uniqueIdClientIdsSetPrefix = sharableConfig.getUniqueIdClientIdsSetPrefix();
        this.payloadCleanWorkInterval = sharableConfig.getCleanWorkInterval();

        Assert.hasText(brokerId, "brokerId can't be null");

        // 启动定时清理任务
        new Thread(this).start();
    }

    /**
     * 存储 publish message 至 redis.
     * <p>
     * 假设主题 "test-topic" 订阅客户端有 10000 个，则每个消息会有 10000 个备份，如果单个消息数据量很大，
     * 那么消息的存储就会出现大量内存、磁盘被占用的情况。
     *
     * @param clientId 客户id
     * @param pubMsg   publish 消息体
     */
    @Override
    public Mono<Void> save(String clientId, PubMsg pubMsg) {
        // 1. 共享主题报文，只需要保存一份，不考虑二级寻址
        // 2. 普通报文，检查报文大小是否超过指定阈值
        //   超过：共享存储
        //   未超过：分别存储

        var messageId = pubMsg.getMessageId();
        if (!isPayloadShouldShare(pubMsg)) {
            pubMsg.setUuid(null); // 非共享载荷不需要 uuid, 减少字段值.
            return redisTemplate.opsForHash()
                    .put(key(clientId), String.valueOf(messageId), serializer.serialize(pubMsg))
                    .then();
        }

        // 报文单独保存
        // redis 中保存
        // 1. payload
        // 2. pubMsg.uniqueId() -> clientId 哪些客户端关联 uniqueId, 只有当 redis set 中的客户端清空后，我们才能删除 redis 中的载荷
        final var payload = pubMsg.getPayload();
        pubMsg.setPayloadSharable(true).setPayload(null);
        var uniqueId = pubMsg.uniqueId();
        var m1 = redisTemplate.opsForHash().put(key(clientId), String.valueOf(messageId), serializer.serialize(pubMsg));
        var m2 = stringRedisTemplate.opsForSet().add(uniqueIdClientIdsSetKey(uniqueId), clientId);
        if (uniqueIdAndPayloadMap.containsKey(uniqueId)) {
            clientIdMessageIdAndUniqueIdMap.put(clientIdMessageIdKey(clientId, messageId), uniqueId);
            return Mono.when(m1, m2);
        } else {
            uniqueIdAndPayloadMap.put(uniqueId, payload);
            clientIdMessageIdAndUniqueIdMap.put(clientIdMessageIdKey(clientId, messageId), uniqueId);

            var m3 = redisTemplate.opsForValue().set(sharablePayloadKey(uniqueId), payload);
            return Mono.when(m1, m2, m3);
        }
    }

    @Override
    public Mono<Void> remove(String clientId, int messageId) {
        // 客户端 publish message 存储移除
        var m1 = redisTemplate.opsForHash()
                .remove(key(clientId), String.valueOf(messageId));

        // 检查 clientId + messageId 是否存在本地关联的 uniqueId
        String uniqueId = clientIdMessageIdAndUniqueIdMap.remove(clientIdMessageIdKey(clientId, messageId));
        if (uniqueId != null) {
            var m2 = stringRedisTemplate.opsForSet()
                    .remove(uniqueIdClientIdsSetKey(uniqueId), clientId);
            return Mono.when(m1, m2);
        }

        // 无本地关联，删除 redis 中的数据
        return redisTemplate.opsForHash()
                .get(key(clientId), String.valueOf(messageId))
                .flatMap(t -> {
                    final var pubMsg = serializer.deserialize((byte[]) t, PubMsg.class);
                    if (pubMsg.isPayloadSharable()) {
                        var m2 = stringRedisTemplate.opsForSet()
                                .remove(uniqueIdClientIdsSetKey(pubMsg.uniqueId()), clientId);
                        return Mono.when(m1, m2);
                    }
                    return m1.then();
                });
    }

    @Override
    public Mono<Void> clear(String clientId) {
        return redisTemplate.opsForHash().values(key(clientId))
                .flatMap(t -> {
                    var value = (byte[]) t;
                    var pubMsg = serializer.deserialize(value, PubMsg.class);
                    if (pubMsg.isPayloadSharable()) {
                        return stringRedisTemplate.opsForSet()
                                .remove(uniqueIdClientIdsSetKey(pubMsg.uniqueId()), clientId)
                                .doOnSuccess(unused -> clientIdMessageIdAndUniqueIdMap.remove(clientIdMessageIdKey(clientId, pubMsg.getMessageId())))
                                .then();
                    }
                    return Mono.empty();
                })
                .then(redisTemplate.delete(key(clientId)))
                .then();
    }

    @Override
    public Flux<PubMsg> search(String clientId) {
        return redisTemplate.opsForHash()
                .values(key(clientId))
                .flatMap(e -> {
                    final var pubMsg = serializer.deserialize((byte[]) e, PubMsg.class);
                    final var uniqueId = pubMsg.uniqueId();
                    if (pubMsg.isPayloadSharable()) {
                        byte[] bytes = uniqueIdAndPayloadMap.get(uniqueId);
                        if (bytes == null) {
                            return redisTemplate.opsForValue().get(sharablePayloadKey(uniqueId)).map(pubMsg::setPayload);
                        }
                        pubMsg.setPayload(bytes);
                    }

                    return Mono.just(pubMsg);
                });
    }

    private String key(String clientId) {
        return pubMsgSetPrefix + clientId;
    }

    private String clientIdMessageIdKey(String clientId, int messageId) {
        return String.format("%s-%d", clientId, messageId);
    }

    /**
     * 这个 key 的格式加入了 broker Id. 使集群中各 broker 独立处理自己的 任务.
     *
     * @param uniqueId {@link PubMsg#uniqueId()}
     */
    private String uniqueIdClientIdsSetKey(String uniqueId) {
        return String.format("%s%s", uniqueIdClientIdsSetPrefix, uniqueId);
    }

    /**
     * 共享 payload key.
     *
     * @param uniqueId {@link PubMsg#uniqueId()}
     */
    private String sharablePayloadKey(String uniqueId) {
        return String.format("%s%s:%s", sharablePayloadKeyPrefix, brokerId, uniqueId);
    }

    /**
     * 消息载荷是否需要多客户端共享.
     * <ol>
     *     <li>消息指定了客户端，无需共享</li>
     *     <li>消息 size 未超过指定值 thresholdInMessage, 无需共享</li>
     * </ol>
     *
     * @param pubMsg publish 消息
     */
    private boolean isPayloadShouldShare(PubMsg pubMsg) {
        if (StringUtils.hasText(pubMsg.getAppointedClientId())) {
            return false;
        }

        byte[] payload = pubMsg.getPayload();
        if (payload == null) {
            log.error("PubMsg[{}:{}]载荷为空", pubMsg.hashCode(), pubMsg);
            return false;
        }

        //noinspection RedundantIfStatement
        if (payload.length < thresholdInMessage) {
            return false;
        }

        return true;
    }

    @Override
    public void run() {
        log.info("启动定时载荷清理任务");

        // 最近一次清理任务完成时间戳
        var latestProcessedTime = new AtomicLong(0);

        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                var now = System.currentTimeMillis();
                if (now - latestProcessedTime.get() < payloadCleanWorkInterval.toMillis()) {
                    // 最少最少，线程也要休眠一分钟
                    TimeUnit.MINUTES.sleep(1);
                    continue;
                }

                log.debug("开始扫描并清理共享载荷...");
                var cdl = new CountDownLatch(1);

                // scan
                var matchStr = String.format("%s%s:", sharablePayloadKeyPrefix, brokerId);
                var scanOptions = ScanOptions.scanOptions()
                        .count(REDIS_SCAN_COUNT)
                        .match(String.format("%s*", matchStr))
                        .build();
                stringRedisTemplate.scan(scanOptions)
                        .flatMap(key -> {
                            var uniqueId = key.replaceAll(matchStr, "");

                            // 删除
                            return stringRedisTemplate.opsForSet().size(uniqueIdClientIdsSetKey(uniqueId))
                                    .filter(l -> l <= 0)
                                    .flatMap(unused -> redisTemplate.delete(key)
                                            .doOnSuccess(l -> {
                                                var ts = Uuids.unixTimestamp(uniqueId);
                                                log.debug("创建于[{}]的共享载荷[{}]已删除", dateTimeFormat(ts), key);
                                            })
                                    );
                        })
                        .then()
                        .doOnError(throwable -> {
                            log.error(String.format("共享载荷清理失败: %s", throwable.getMessage()), throwable);
                            latestProcessedTime.set(System.currentTimeMillis());

                            cdl.countDown();
                        })
                        .doOnSuccess(unused -> {
                            var end = System.currentTimeMillis();
                            latestProcessedTime.set(end);
                            log.debug("共享载荷清理完成, 耗时: {}ms.", end - now);

                            cdl.countDown();
                        })
                        .subscribe();

                cdl.await();
            } catch (Throwable throwable) {
                log.error(String.format("定时载荷清理任务异常: %s", throwable.getMessage()), throwable);
            }
        }
    }

    private String dateTimeFormat(long ts) {
        var dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), EAST_8);
        return dateTime.format(DF);
    }
}
