package com.jun.mqttx.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.constants.InternalMessageEnum;
import com.jun.mqttx.consumer.Watcher;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.entity.ClientSubOrUnsubMsg;
import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.entity.Tuple2;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private static final int ASSUME_COUNT = 100_000;
    /** 按顺序 -> 订阅、解除订阅 */
    private static final int SUB = 1, UN_SUB = 2;
    private final ReactiveStringRedisTemplate stringRedisTemplate;
    private final Serializer serializer;
    private final IInternalMessagePublishService internalMessagePublishService;
    /** client订阅主题, 订阅主题前缀, 主题集合 */
    private final String clientTopicsPrefix, topicSetKey, topicPrefix;
    private final boolean enableInnerCache, enableCluster;
    private final String brokerId;


    /*                                              非系统主题                                                                */
    /** 包含通配符 # 和 + 的 topic 集合，存储于内存中 */
    private final Set<String> incWildcardTopics = ConcurrentHashMap.newKeySet(ASSUME_COUNT);
    /** 不含通配符 # 和 + 的 topic 集合，存储于内存中 */
    private final Set<String> nonWildcardTopics = ConcurrentHashMap.newKeySet(ASSUME_COUNT);
    /** topic -> clients 关系集合，clients 不区分 cleanSession 和 通配符 */
    private final Map<String, ConcurrentHashMap.KeySetView<ClientSub, Boolean>> allTopicClientsMap = new ConcurrentHashMap<>(ASSUME_COUNT);
    /** client -> topics 关系集合，仅缓存 cleanSession == true 对应的 topics */
    private final Map<String, ConcurrentHashMap.KeySetView<String, Boolean>> inMemClientTopicsMap = new ConcurrentHashMap<>(ASSUME_COUNT);

    /*                                               系统主题                                                                  */
    /** 系统主题 -> clients map */
    private final Map<String, ConcurrentHashMap.KeySetView<ClientSub, Boolean>> sysTopicClientsMap = new ConcurrentHashMap<>();

    //@formatter:on

    public DefaultSubscriptionServiceImpl(ReactiveStringRedisTemplate stringRedisTemplate,
                                          MqttxConfig mqttxConfig,
                                          Serializer serializer,
                                          @Nullable IInternalMessagePublishService internalMessagePublishService) {
        Assert.notNull(stringRedisTemplate, "stringRedisTemplate can't be null");

        this.stringRedisTemplate = stringRedisTemplate;
        this.serializer = serializer;
        this.internalMessagePublishService = internalMessagePublishService;
        this.clientTopicsPrefix = mqttxConfig.getRedis().getClientTopicSetPrefix();
        this.topicPrefix = mqttxConfig.getRedis().getTopicPrefix();
        this.topicSetKey = mqttxConfig.getRedis().getTopicSetKey();

        var cluster = mqttxConfig.getCluster();
        this.enableCluster = cluster.getEnable();
        this.enableInnerCache = mqttxConfig.getEnableInnerCache();
        if (enableInnerCache) {
            // 非测试模式，初始化缓存
            initInnerCache(stringRedisTemplate);
        }
        this.brokerId = mqttxConfig.getBrokerId();

        Assert.hasText(this.topicPrefix, "topicPrefix can't be null");
        Assert.hasText(this.topicSetKey, "topicSetKey can't be null");
    }

    /**
     * 订阅主题
     *
     * @param clientSub 客户订阅信息
     */
    @Override
    public Mono<Void> subscribe(ClientSub clientSub) {
        final var topic = clientSub.getTopic();
        final var clientId = clientSub.getClientId();
        final var qos = clientSub.getQos();
        final var cleanSession = clientSub.isCleanSession();

        // 保存订阅关系到本地缓存
        // 1. 保存 topic -> client 映射
        // 2. 将 topic 保存到 redis set 集合中
        // 3. 保存 client -> topics
        // 保存订阅关系到本地缓存
        subscribeWithCache(clientSub);

        // 广播订阅事件
        if (enableCluster) {
            var im = new InternalMessage<>(
                    new ClientSubOrUnsubMsg(clientId, qos, topic, cleanSession, null, SUB),
                    System.currentTimeMillis(),
                    brokerId
            );
            internalMessagePublishService.publish(im, InternalMessageEnum.SUB_UNSUB.getChannel());
        }

        // cleanSession = false 保存订阅关系到 redis
        if (!cleanSession) {
            return Mono.when(
                    stringRedisTemplate.opsForHash().put(topicPrefix + topic, clientId, String.valueOf(qos)),
                    stringRedisTemplate.opsForSet().add(topicSetKey, topic),
                    stringRedisTemplate.opsForSet().add(clientTopicsPrefix + clientId, topic)
            );
        }

        return Mono.empty();
    }

    /**
     * 解除订阅
     *
     * @param clientId     客户 id
     * @param cleanSession clientId 关联会话 cleanSession 状态
     * @param topics       主题列表
     */
    @Override
    public Mono<Void> unsubscribe(String clientId, boolean cleanSession, List<String> topics) {
        if (CollectionUtils.isEmpty(topics)) {
            return Mono.empty();
        }

        // 将订阅关系从本地缓存移除
        unsubscribeWithCache(clientId, topics);

        // 集群广播
        if (enableCluster) {
            var im = new InternalMessage<>(
                    new ClientSubOrUnsubMsg(clientId, 0, null, cleanSession, topics, UN_SUB)
                    , System.currentTimeMillis(),
                    brokerId);
            internalMessagePublishService.publish(im, InternalMessageEnum.SUB_UNSUB.getChannel());
        }

        if (!cleanSession) {
            var monos = topics.stream()
                    .map(topic -> stringRedisTemplate.opsForHash().remove(topicPrefix + topic, clientId))
                    .toList();
            return Mono.when(monos)
                    .then(stringRedisTemplate.opsForSet().remove(clientTopicsPrefix + clientId, topics.toArray()))
                    .then();
        }

        return Mono.empty();
    }


    /**
     * 返回订阅主题的客户列表。考虑到 pub 类别的消息最为频繁且每次 pub 都会触发 <code>searchSubscribeClientList(String topic)</code>
     * 方法，所以增加内部缓存以优化该方法的执行逻辑。
     *
     * @param topic 主题
     * @return 客户端订阅信息
     */
    @Override
    public Flux<ClientSub> searchSubscribeClientList(String topic) {
        // 启用内部缓存机制
        if (enableInnerCache) {
            return Flux.fromIterable(searchSubscribeClientListByCache(topic));
        }

        // 未启用内部缓存机制，直接通过 redis 抓取
        List<ClientSub> clientSubList = new ArrayList<>();

        // 分两部分
        // 一部分是 cleanSession(true) 主题
        // 另外一部分是 cleanSession(false) 主题
        incWildcardTopics.stream()
                .filter(t -> TopicUtils.match(topic, t))
                .forEach(t -> {
                    var clientSubs = allTopicClientsMap.get(t);
                    if (!CollectionUtils.isEmpty(clientSubs)) {
                        clientSubList.addAll(clientSubs);
                    }
                });
        if (nonWildcardTopics.contains(topic)) {
            var clientSubs = allTopicClientsMap.get(topic);
            if (!CollectionUtils.isEmpty(clientSubs)) {
                clientSubList.addAll(clientSubs);
            }
        }
        return stringRedisTemplate.opsForSet().members(topicSetKey)
                .filter(t -> TopicUtils.match(topic, t))
                .flatMap(t -> stringRedisTemplate.opsForHash().entries(topicPrefix + t)
                        .map(entry -> {
                            var clientId = (String) entry.getKey();
                            var qosStr = (String) entry.getValue();
                            return ClientSub.of(clientId, Integer.parseInt(qosStr), t, false);
                        })
                ).concatWith(Flux.fromIterable(clientSubList));
    }

    @Override
    public Mono<Void> clearClientSubscriptions(String clientId, boolean cleanSession) {
        if (cleanSession) {
            var keys = inMemClientTopicsMap.remove(clientId);
            if (CollectionUtils.isEmpty(keys)) {
                return Mono.empty();
            }
            return unsubscribe(clientId, true, new ArrayList<>(keys));
        } else {
            return stringRedisTemplate.opsForSet().members(clientTopicsPrefix + clientId)
                    .collectList()
                    .flatMap(topics -> stringRedisTemplate.delete(clientTopicsPrefix + clientId)
                            .flatMap(unused -> unsubscribe(clientId, false, new ArrayList<>(topics)))
                    );
        }
    }

    @Override
    public Mono<Void> clearUnAuthorizedClientSub(String clientId, List<String> authorizedSub) {
        var collect = nonWildcardTopics
                .stream()
                .filter(topic -> !authorizedSub.contains(topic))
                .collect(Collectors.toSet());

        collect.addAll(incWildcardTopics
                .stream()
                .filter(topic -> !authorizedSub.contains(topic))
                .collect(Collectors.toSet())
        );

        return Mono.when(
                unsubscribe(clientId, false, new ArrayList<>(collect)),
                unsubscribe(clientId, true, new ArrayList<>(collect)));
    }


    @Override
    public void action(byte[] msg) {
        InternalMessage<ClientSubOrUnsubMsg> im;
        if (serializer instanceof JsonSerializer se) {
            im = se.deserialize(msg, new TypeReference<>() {
            });
        } else {
            //noinspection unchecked
            im = serializer.deserialize(msg, InternalMessage.class);
        }
        final var data = im.getData();
        final var type = data.getType();
        final var clientId = data.getClientId();
        switch (type) {
            case SUB -> subscribeWithCache(ClientSub.of(clientId, data.getQos(), data.getTopic(), data.isCleanSession()));
            case UN_SUB -> unsubscribeWithCache(clientId, data.getTopics())
                    .doOnError(throwable -> log.error(throwable.getMessage(), throwable))
                    .subscribe();
            default -> log.error("非法的 ClientSubOrUnsubMsg: [{}] ", data);
        }
    }

    @Override
    public boolean support(String channel) {
        return InternalMessageEnum.SUB_UNSUB.getChannel().equals(channel);
    }

    /**
     * 初始化内部缓存。目前的策略是全部加载，其实可以按需加载，按业务需求来吧。
     */
    private void initInnerCache(final ReactiveStringRedisTemplate redisTemplate) {
        log.info("enableInnerCache=true, 开始加载缓存...");

        redisTemplate.opsForSet().members(topicSetKey)
                .collectList()
                .doOnSuccess(topics -> {
                    for (var topic : topics) {
                        if (TopicUtils.isTopicContainWildcard(topic)) {
                            incWildcardTopics.add(topic);
                        } else {
                            nonWildcardTopics.add(topic);
                        }
                    }
                })
                .flatMapIterable(Function.identity())
                .flatMap(topic -> redisTemplate.opsForHash().entries(topicPrefix + topic).map(e -> new Tuple2<>(topic, e)))
                .doOnNext(e -> {
                    var topic = e.t0();
                    var k = (String) e.t1().getKey();
                    var v = (String) e.t1().getValue();
                    allTopicClientsMap.computeIfAbsent(topic, s -> ConcurrentHashMap.newKeySet())
                            .add(ClientSub.of(k, Integer.parseInt(v), topic, false));
                })
                .then()
                .doOnError(t -> log.error(t.getMessage(), t))
                // 这里我们应该阻塞
                .block();
    }

    /**
     * 通过缓存获取客户端订阅列表
     *
     * @param topic 待匹配主题
     * @return 客户端订阅列表
     */
    private List<ClientSub> searchSubscribeClientListByCache(String topic) {
        // result
        List<ClientSub> clientSubList = new ArrayList<>();

        // 这里需要注意的几点
        // 1 非通配符集合，先判断是否存在
        // 2 通配符集合必须都遍历一遍，因为你不确定哪个通配符主题匹配当前主题

        // 1 非通配符主题集合
        if (nonWildcardTopics.contains(topic)) {
            ConcurrentHashMap.KeySetView<ClientSub, Boolean> clientSubs = allTopicClientsMap.get(topic);
            if (!CollectionUtils.isEmpty(clientSubs)) {
                clientSubList.addAll(clientSubs);
            }
        }

        /*
         * HACK 可以加定制化功能提升性能，如：在定义业务 topic 的时候，以 up / down 开头，以下代码判断是 up 字符开头，则进入循环。
         *  实际业务场景中，下行的消息是平台下发到设备，设备需要订阅完整 topic，上行的消息是设备发送到平台，平台需要订阅通配符的 topic。
         *  结合 ACL 访问控制策略使用，可以将订阅含通配符的 topic 数量控制到极少，业务上正确使用的话，以下 for 循环的执行效率会非常高。
         */
        // 2 含通配符主题集合
        for (String t : incWildcardTopics) {
            if (TopicUtils.match(topic, t)) {
                ConcurrentHashMap.KeySetView<ClientSub, Boolean> clientSubs = allTopicClientsMap.get(t);
                if (!CollectionUtils.isEmpty(clientSubs)) {
                    clientSubList.addAll(clientSubs);
                }
            }
        }

        return clientSubList;
    }

    /**
     * 移除缓存中的订阅
     *
     * @param clientId 客户端ID
     * @param topics   主题列表
     */
    private Mono<Void> unsubscribeWithCache(String clientId, List<String> topics) {
        if (!enableInnerCache) {
            return Mono.empty();
        }

        // 待删除的主题(当主题没有客户端订阅后)
        var waitToDel = new ArrayList<String>();
        topics.forEach(topic -> {
            ConcurrentHashMap.KeySetView<ClientSub, Boolean> clientSubs = allTopicClientsMap.get(topic);
            allTopicClientsMap.get(topic).stream()
                    .filter(clientSub -> clientSub.getClientId().equals(clientId))
                    .forEach(clientSubs::remove);

            if (CollectionUtils.isEmpty(clientSubs)) {
                waitToDel.add(topic);
            }

            if (CollectionUtils.isEmpty(allTopicClientsMap.get(topic))) {
                if (TopicUtils.isTopicContainWildcard(topic)) {
                    incWildcardTopics.remove(topic);
                } else {
                    nonWildcardTopics.remove(topic);
                }
            }
        });

        if (!waitToDel.isEmpty()) {
            return stringRedisTemplate.opsForSet().remove(topicSetKey, waitToDel.toArray()).then();
        }

        return Mono.empty();
    }

    /**
     * 将客户端订阅存储到缓存
     *
     * @param clientSub 客户端端订阅
     */
    private void subscribeWithCache(ClientSub clientSub) {
        String topic = clientSub.getTopic();
        String clientId = clientSub.getClientId();

        if (TopicUtils.isTopicContainWildcard(topic)) {
            incWildcardTopics.add(topic);
        } else {
            nonWildcardTopics.add(topic);
        }
        allTopicClientsMap.computeIfAbsent(topic, s -> ConcurrentHashMap.newKeySet()).add(clientSub);
        inMemClientTopicsMap.computeIfAbsent(clientId, s -> ConcurrentHashMap.newKeySet()).add(topic);
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
}
