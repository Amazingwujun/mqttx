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

import com.fasterxml.jackson.core.type.TypeReference;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.constants.InternalMessageEnum;
import com.jun.mqttx.consumer.Watcher;
import com.jun.mqttx.entity.*;
import com.jun.mqttx.service.IInternalMessagePublishService;
import com.jun.mqttx.service.ISubscriptionService;
import com.jun.mqttx.utils.JsonSerializer;
import com.jun.mqttx.utils.Serializer;
import com.jun.mqttx.utils.TopicUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * <h1>主题订阅服务</h1>
 * <p>
 * 为了优化 cleanSession = 1 会话的性能，所有与之相关的状态均保存在内存当中.
 *
 * @author Jun
 * @since 1.0.4
 */
@Slf4j
@Service
public class DefaultSubscriptionServiceImpl implements ISubscriptionService, Watcher {
    //@formatter:off

    /** 用于分割字符，刻意设计成这样，防止与 clientId 中的字符重合 */
    private static final String COMPLEX_SEPARATOR = "<!>";
    private static final String COMMA_SEPARATOR = ",";
    private static final int ASSUME_COUNT = 100_000;
    /** 按顺序 -> 订阅、解除订阅 */
    private static final int SUB = 1, UN_SUB = 2;
    private final ReactiveStringRedisTemplate stringRedisTemplate;
    private final Serializer serializer;
    private final IInternalMessagePublishService internalMessagePublishService;
    /** client订阅主题, 订阅主题前缀, 主题集合 */
    private final String clientTopicsPrefix, topicSetKey, topicPrefix;
    private final boolean enableCluster;
    private final String brokerId;
    /**
     * cleanSession == true 的主 client -> topics 关系集合.
     * <p>
     * 此集合仅用于保存与当前 mqttx 实例存在 tcp 链接的客户端
     */
    private final Map<String, HashSet<String>> inMemClientTopicsMap = new ConcurrentHashMap<>(ASSUME_COUNT);
    /** 不含通配符的全部主题 */
    private final Set<String> noneWildcardTopics = ConcurrentHashMap.newKeySet(ASSUME_COUNT);
    /** 包含通配符的全部主题 */
    private final Set<String> hasWildcardTopics = ConcurrentHashMap.newKeySet(ASSUME_COUNT);
    /** topic -> clients 映射关系集合. */
    private final Map<String, Set<ClientSub>> topicClientsMap = new ConcurrentHashMap<>(ASSUME_COUNT);
    /** 系统主题 -> clients map */
    private final Map<String, ConcurrentHashMap.KeySetView<ClientSub, Boolean>> sysTopicClientsMap = new ConcurrentHashMap<>();

    //@formatter:on

    public DefaultSubscriptionServiceImpl(ReactiveStringRedisTemplate stringRedisTemplate,
                                          MqttxConfig mqttxConfig,
                                          Serializer serializer,
                                          @Nullable IInternalMessagePublishService internalMessagePublishService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.serializer = serializer;
        this.internalMessagePublishService = internalMessagePublishService;
        var redisKey = mqttxConfig.getRedis();
        this.clientTopicsPrefix = redisKey.getClientTopicSetPrefix();
        this.topicPrefix = redisKey.getTopicPrefix();
        this.topicSetKey = redisKey.getTopicSetKey();

        var cluster = mqttxConfig.getCluster();
        this.enableCluster = cluster.getEnable();
        this.brokerId = mqttxConfig.getBrokerId();

        // 内部缓存初始化
        initInnerCache(stringRedisTemplate);
    }

    /**
     * 订阅主题
     *
     * @param clientSub 客户订阅信息
     */
    @Override
    public Mono<Void> subscribe(ClientSub clientSub) {
        return subscribe(clientSub, false);
    }

    /**
     * 解除订阅
     *
     * @param clientId     客户 id
     * @param cleanSession clientId 关联会话 cleanSession 状态
     * @param topics       主题列表，可能包含共享主题
     */
    public Mono<Void> unsubscribe(String clientId, boolean cleanSession, List<String> topics) {
        return unsubscribe(clientId, cleanSession, topics, false);
    }


    /**
     * 返回订阅主题的客户列表。考虑到 pub 类别的消息最为频繁且每次 pub 都会触发 <code>searchSubscribeClientList(String topic)</code>
     * 方法，所以增加内部缓存以优化该方法的执行逻辑。
     *
     * @param topic 主题, 此为 publish message 中包含的 topic.
     * @return 客户端订阅信息
     */
    @Override
    public Flux<ClientSub> searchSubscribeClientList(String topic) {
        // result
        var clientSubList = new ArrayList<ClientSub>();

        // 这里需要注意的几点
        // 1 通配符集合必须都遍历一遍，因为你不确定哪个通配符主题匹配当前主题
        // 2 非通配符集合，先判断是否存在

        // 1 含通配符主题集合
        for (var t : hasWildcardTopics) {
            if (TopicUtils.match(topic, t)) {
                topicClientsMap.computeIfPresent(t, (k, v) -> {
                    clientSubList.addAll(v);
                    return v;
                });
            }
        }

        // 2 不含通配符主题集合
        if (noneWildcardTopics.contains(topic)) {
            topicClientsMap.computeIfPresent(topic, (k, v) -> {
                clientSubList.addAll(v);
                return v;
            });
        }

        return Flux.fromIterable(clientSubList);
    }

    @Override
    public Mono<Void> clearClientSubscriptions(String clientId, boolean cleanSession) {
        Set<String> keys;
        if (cleanSession) {
            keys = inMemClientTopicsMap.remove(clientId);
            if (CollectionUtils.isEmpty(keys)) {
                return Mono.empty();
            }
            return unsubscribe(clientId, true, new ArrayList<>(keys));
        } else {
            return stringRedisTemplate.opsForSet().members(clientTopicsPrefix + clientId)
                    .collectList()
                    .flatMap(e -> stringRedisTemplate.delete(clientTopicsPrefix + clientId)
                            .flatMap(unused -> unsubscribe(clientId, false, new ArrayList<>(e)))
                    );
        }
    }

    @Override
    public Mono<Void> clearUnAuthorizedClientSub(String clientId, List<String> authorizedSub) {
        var collect = new ArrayList<String>();
        for (var topic : noneWildcardTopics) {
            if (!authorizedSub.contains(topic)) {
                collect.add(topic);
            }
        }
        for (var topic : hasWildcardTopics) {
            if (!authorizedSub.contains(topic)) {
                collect.add(topic);
            }
        }
        return Mono.when(unsubscribe(clientId, false, collect), unsubscribe(clientId, true, collect));
    }


    @Override
    public void action(byte[] msg) {
        InternalMessage<ClientSubOrUnsubMsg> im;
        if (serializer instanceof JsonSerializer) {
            im = ((JsonSerializer) serializer).deserialize(msg, new TypeReference<>() {
            });
        } else {
            //noinspection unchecked
            im = serializer.deserialize(msg, InternalMessage.class);
        }
        final var data = im.getData();
        final var type = data.getType();
        final var clientId = data.getClientId();
        var topic = data.getTopic();
        final var cleanSession = data.isCleanSession();

        // 共享主题处理
        String sn = null;
        if (TopicUtils.isShare(topic)) {
            ShareTopic shareTopic = TopicUtils.parseFrom(topic);
            sn = shareTopic.name();
            topic = shareTopic.filter();
        }
        final var filter = topic;
        final var shareName = sn;


        switch (type) {
            case SUB -> {
                var clientSub = ClientSub.of(clientId, data.getQos(), filter, cleanSession, shareName);
                subscribe(clientSub, true).subscribe();
            }
            case UN_SUB -> {
                var topics = data.getTopics();
                unsubscribe(clientId, cleanSession, topics, true).subscribe();
            }
            default -> log.error("非法的 ClientSubOrUnsubMsg: [{}] ", data);
        }
    }

    @Override
    public boolean support(String channel) {
        return InternalMessageEnum.SUB_UNSUB.getChannel().equals(channel);
    }

    /**
     * 缓存初始化.
     */
    private void initInnerCache(final ReactiveStringRedisTemplate redisTemplate) {
        log.info("开始加载缓存...");

        // inDisk 订阅关系加载
        redisTemplate.opsForSet().scan(topicSetKey)
                .map(topic -> {
                    if (TopicUtils.isShare(topic)) {
                        topic = TopicUtils.parseFrom(topic).filter();
                    }
                    return topic;
                })
                .distinct()
                .collectList()
                .doOnSuccess(topics -> {
                    for (var topic : topics) {
                        if (TopicUtils.isTopicContainWildcard(topic)) {
                            hasWildcardTopics.add(topic);
                        } else {
                            noneWildcardTopics.add(topic);
                        }
                    }
                })
                .flatMapIterable(Function.identity())
                .flatMap(topic -> redisTemplate.opsForHash().entries(topicPrefix + topic).map(e -> new Tuple2<>(topic, e)))
                .doOnNext(e -> {
                    var topic = e.t0();
                    // k
                    var k = (String) e.t1().getKey();
                    var k_s = k.split(COMPLEX_SEPARATOR);

                    // v
                    var v = (String) e.t1().getValue();
                    var v_s = v.split(COMMA_SEPARATOR);
                    var qosStr = v_s[0];
                    var cs = "0";
                    if (v_s.length > 1) {
                        cs = v_s[1];
                    }
                    var cleanSession = "1".equals(cs);

                    if (k_s.length == 1) {
                        topicClientsMap.compute(topic, (j, z) -> {
                            if (z == null) {
                                z = new HashSet<>();
                            }

                            z.add(ClientSub.of(k, Integer.parseInt(qosStr), topic, cleanSession));
                            return z;
                        });
                    } else {
                        var shareName = k_s[1];
                        topicClientsMap.compute(topic, (j, z) -> {
                            if (z == null) {
                                z = new HashSet<>();
                            }

                            z.add(ClientSub.of(k_s[0], Integer.parseInt(qosStr), topic, cleanSession, shareName));
                            return z;
                        });
                    }
                })
                .then()
                .doOnError(t -> log.error(t.getMessage(), t))
                // 这里我们应该阻塞
                .block();

        log.info("缓存加载完成.");
    }

    /**
     * 客户端订阅主题
     *
     * @param clientSub        客户端订阅信息
     * @param isClusterMessage 调用消息是否源自集群
     */
    private Mono<Void> subscribe(ClientSub clientSub, boolean isClusterMessage) {
        final var topic = clientSub.getTopic();
        final var clientId = clientSub.getClientId();
        final var qos = clientSub.getQos();
        final var cleanSession = clientSub.isCleanSession();
        final var shareName = clientSub.getShareName();
        var filter = topic;
        if (StringUtils.hasText(shareName)) {
            filter = String.format("%s/%s/%s", TopicUtils.SHARE_TOPIC, shareName, topic);
        }
        final var topicFilter = filter;

        // 保存订阅关系到应用缓存
        topicClientsMap.compute(topic, (k, v) -> {
            if (v == null) {
                v = new HashSet<>();
                v.add(clientSub);
                return v;
            }
            v.remove(clientSub);
            v.add(clientSub);
            return v;
        });
        if (TopicUtils.isTopicContainWildcard(topic)) {
            hasWildcardTopics.add(topic);
        } else {
            noneWildcardTopics.add(topic);
        }

        // 集群消息，直接返回
        if (isClusterMessage) {
            return Mono.empty();
        }

        // 针对客户端 cleanSession == true 的会话，如果 mqttx 是单机模式，那么订阅数据仅保存到缓存中.
        if (cleanSession) {
            inMemClientTopicsMap.compute(clientId, (k, v) -> {
                if (v == null) {
                    v = new HashSet<>();
                }
                v.add(topicFilter);
                return v;
            });
            if (!enableCluster) {
                return Mono.empty();
            }
        }

        // 订阅关系保存到 redis
        return Mono.when(
                stringRedisTemplate.opsForHash().put(topicPrefix + topic, topicClientSubKey(clientId, shareName), topicClientSubValue(qos, cleanSession)),
                stringRedisTemplate.opsForSet().add(topicSetKey, topicFilter),
                // 类似 inMemClientTopicsMap#key, 这里也必须存 topicFilter
                stringRedisTemplate.opsForSet().add(clientTopicsPrefix + clientId, topicFilter)
        ).then(Mono.fromRunnable(() -> {
            if (enableCluster) {
                var im = new InternalMessage<>(
                        new ClientSubOrUnsubMsg(clientId, qos, topicFilter, cleanSession, null, SUB),
                        System.currentTimeMillis(),
                        brokerId
                );
                internalMessagePublishService.publish(im, InternalMessageEnum.SUB_UNSUB.getChannel());
            }
        }));
    }

    /**
     * 解除订阅
     *
     * @param clientId     客户 id
     * @param cleanSession clientId 关联会话 cleanSession 状态
     * @param topics       主题列表，可能包含共享主题
     */
    private Mono<Void> unsubscribe(String clientId, boolean cleanSession, List<String> topics, boolean isClusterMessage) {
        if (CollectionUtils.isEmpty(topics)) {
            return Mono.empty();
        }

        // 待删除的主题(当主题没有客户端订阅后)
        // 减少 redis 中无效的 key
        var waitToDel = new ArrayList<String>();

        // 先移除缓存
        topics.forEach(topic -> {
            // 共享主题判断
            String shareName = null;
            if (TopicUtils.isShare(topic)) {
                ShareTopic shareTopic = TopicUtils.parseFrom(topic);
                topic = shareTopic.filter();
                shareName = shareTopic.name();
            }
            final var fixTopic = topic;
            final var fixShareName = shareName;

            // 移除主题关联关系
            topicClientsMap.computeIfPresent(fixTopic, (k, v) -> {
                v.remove(ClientSub.of(clientId, 0, fixTopic, cleanSession, fixShareName));
                if (v.isEmpty()) {
                    waitToDel.add(fixTopic);

                    // 移除关联的 inMemTopic
                    if (TopicUtils.isTopicContainWildcard(fixTopic)) {
                        hasWildcardTopics.remove(fixTopic);
                    } else {
                        noneWildcardTopics.remove(fixTopic);
                    }
                }
                return v;
            });
        });

        // 集群消息，直接返回
        if (isClusterMessage) {
            return Mono.empty();
        }

        // 与 this#subscribe 对应
        if (cleanSession) {
            inMemClientTopicsMap.computeIfPresent(clientId, (k, v) -> {
                topics.forEach(v::remove);
                return v;
            });
            if (!enableCluster) {
                return Mono.empty();
            }
        }


        // 移除 redis 中的数据.
        var monos = topics.stream()
                .map(topic -> {
                    if (TopicUtils.isShare(topic)) {
                        ShareTopic shareTopic = TopicUtils.parseFrom(topic);
                        var shareName = shareTopic.name();
                        topic = shareTopic.filter();
                        return stringRedisTemplate.opsForHash().remove(topicPrefix + topic, topicClientSubKey(clientId, shareName));
                    }
                    return stringRedisTemplate.opsForHash().remove(topicPrefix + topic, clientId);
                })
                .toList();
        return Mono.when(monos)
                .then(stringRedisTemplate.opsForSet().remove(clientTopicsPrefix + clientId, topics.toArray()))
                .flatMap(t -> {
                    if (waitToDel.isEmpty()) {
                        return Mono.empty();
                    }

                    return stringRedisTemplate.opsForSet().remove(topicSetKey, waitToDel.toArray()).then();
                })
                .doOnSuccess(unused -> {
                    // 集群广播
                    if (enableCluster) {
                        var clientSubOrUnsubMsg = new ClientSubOrUnsubMsg(clientId, 0, null, cleanSession, topics, UN_SUB);
                        var im = new InternalMessage<>(clientSubOrUnsubMsg, System.currentTimeMillis(), brokerId);
                        internalMessagePublishService.publish(im, InternalMessageEnum.SUB_UNSUB.getChannel());
                    }
                });

    }

    @Override
    public Flux<ClientSub> searchSysTopicClients(String topic) {
        // result
        List<ClientSub> clientSubList = new ArrayList<>();

        sysTopicClientsMap.forEach((wildTopic, set) -> {
            if (TopicUtils.match(topic, wildTopic)) {
                clientSubList.addAll(set);
            }
        });

        return Flux.fromIterable(clientSubList);
    }

    @Override
    public Mono<Void> subscribeSys(ClientSub clientSub) {
        sysTopicClientsMap.computeIfAbsent(clientSub.getTopic(), k -> ConcurrentHashMap.newKeySet()).add(clientSub);
        return Mono.empty();
    }

    @Override
    public Mono<Void> unsubscribeSys(String clientId, List<String> topics) {
        for (String topic : topics) {
            var clientSubs = sysTopicClientsMap.get(topic);
            if (!CollectionUtils.isEmpty(clientSubs)) {
                clientSubs.remove(ClientSub.of(clientId, 0, topic, false));
            }
        }

        return Mono.empty();
    }

    @Override
    public Mono<Void> clearClientSysSub(String clientId) {
        sysTopicClientsMap.forEach((topic, clientSubs) -> clientSubs.remove(ClientSub.of(clientId, 0, topic, false)));
        return Mono.empty();
    }

    /**
     * 主题关联的用户订阅信息 redis hashmap key
     *
     * @param clientId  客户端 id
     * @param shareName 共享主题
     */
    private String topicClientSubKey(String clientId, String shareName) {
        if (StringUtils.hasText(shareName)) {
            return String.format("%s%s%s", clientId, COMPLEX_SEPARATOR, shareName);
        }
        return clientId;
    }

    /**
     * 主题关联的用户订阅信息 redis hashmap value
     *
     * @param qos          {@link io.netty.handler.codec.mqtt.MqttQoS}
     * @param cleanSession cleanSession 状态值
     */
    private String topicClientSubValue(int qos, boolean cleanSession) {
        return String.format("%d%s%d", qos, COMMA_SEPARATOR, cleanSession ? 1 : 0);
    }
}
