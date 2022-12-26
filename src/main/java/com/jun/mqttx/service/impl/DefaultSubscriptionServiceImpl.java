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


    /*                                              cleanSession = 1                                                   */

    /** cleanSession = 1 无通配符主题集合，存储于内存中 */
    private final Set<String> inMemNoneWildcardTopics = ConcurrentHashMap.newKeySet(ASSUME_COUNT);
    /** cleanSession = 1 含通配符主题集合，存储于内存中 */
    private final Set<String> inMemWildcardTopics = ConcurrentHashMap.newKeySet(ASSUME_COUNT);
    /** cleanSession = 0 的主 topic -> clients 关系集合 */
    private final Map<String, ConcurrentHashMap.KeySetView<ClientSub,Boolean>> inMemTopicClientsMap = new ConcurrentHashMap<>(ASSUME_COUNT);
    /** cleanSession = 0 的主 client -> topics 关系集合 */
    private final Map<String, ConcurrentHashMap.KeySetView<String,Boolean>> inMemClientTopicsMap = new ConcurrentHashMap<>(ASSUME_COUNT);

    /*                                              cleanSession = 0                                                   */

    /** cleanSession = 0 无通配符主题集合。内部缓存，{@link this#enableInnerCache} == true 时使用 */
    private final Set<String> inDiskNoneWildcardTopics = ConcurrentHashMap.newKeySet(ASSUME_COUNT);
    /** cleanSession = 0 含通配符主题集合。内部缓存，{@link this#enableInnerCache} == true 时使用 */
    private final Set<String> inDiskWildcardTopics = ConcurrentHashMap.newKeySet(ASSUME_COUNT);
    /** cleanSession = 0 的主 topic -> clients 关系集合 */
    private final Map<String, ConcurrentHashMap.KeySetView<ClientSub, Boolean>> inDiskTopicClientsMap = new ConcurrentHashMap<>(ASSUME_COUNT);

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

        // 保存订阅关系
        // 1. 保存 topic -> client 映射
        // 2. 将 topic 保存到 redis set 集合中
        // 3. 保存 client -> topics
        if (cleanSession) {
            inMemTopicClientsMap
                    .computeIfAbsent(topic, s -> ConcurrentHashMap.newKeySet())
                    .add(clientSub);
            if (TopicUtils.isTopicContainWildcard(topic)) {
                inMemWildcardTopics.add(topic);
            } else {
                inMemNoneWildcardTopics.add(topic);
            }
            inMemClientTopicsMap
                    .computeIfAbsent(clientId, s -> ConcurrentHashMap.newKeySet())
                    .add(topic);

            if (enableCluster) {
                var im = new InternalMessage<>(
                        new ClientSubOrUnsubMsg(clientId, qos, topic, true, null, SUB),
                        System.currentTimeMillis(),
                        brokerId
                );
                internalMessagePublishService.publish(im, InternalMessageEnum.SUB_UNSUB.getChannel());
            }
            return Mono.empty();
        } else {
            return Mono.when(
                    stringRedisTemplate.opsForHash().put(topicPrefix + topic, clientId, String.valueOf(qos)),
                    stringRedisTemplate.opsForSet().add(topicSetKey, topic),
                    stringRedisTemplate.opsForSet().add(clientTopicsPrefix + clientId, topic)
            ).then(Mono.fromRunnable(() -> {
                if (enableInnerCache) {
                    subscribeWithCache(clientSub);
                }

                if (enableCluster) {
                    var im = new InternalMessage<>(
                            new ClientSubOrUnsubMsg(clientId, qos, topic, false, null, SUB),
                            System.currentTimeMillis(),
                            brokerId
                    );
                    internalMessagePublishService.publish(im, InternalMessageEnum.SUB_UNSUB.getChannel());
                }
            }));
        }
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

        if (cleanSession) {
            topics.forEach(topic -> {
                var clientSubs = inMemTopicClientsMap.get(topic);
                if (!CollectionUtils.isEmpty(clientSubs)) {
                    clientSubs.remove(ClientSub.of(clientId, 0, topic, false));
                    if (clientSubs.isEmpty()) {
                        // 移除关联的 inMemTopic
                        if (TopicUtils.isTopicContainWildcard(topic)) {
                            inMemWildcardTopics.remove(topic);
                        } else {
                            inMemNoneWildcardTopics.remove(topic);
                        }
                    }
                }
            });
            Optional.ofNullable(inMemClientTopicsMap.get(clientId)).ifPresent(t -> t.removeAll(topics));

            // 集群广播
            if (enableCluster) {
                var clientSubOrUnsubMsg = new ClientSubOrUnsubMsg(clientId, 0, null, true, topics, UN_SUB);
                var im = new InternalMessage<>(clientSubOrUnsubMsg, System.currentTimeMillis(), brokerId);
                internalMessagePublishService.publish(im, InternalMessageEnum.SUB_UNSUB.getChannel());
            }
            return Mono.empty();
        } else {
            var monos = topics.stream()
                    .map(topic -> stringRedisTemplate.opsForHash().remove(topicPrefix + topic, clientId))
                    .toList();
            return Mono.when(monos)
                    .then(stringRedisTemplate.opsForSet().remove(clientTopicsPrefix + clientId, topics.toArray()))
                    .flatMap(t -> unsubscribeWithCache(clientId, topics))
                    .doOnSuccess(unused -> {
                        // 集群广播
                        if (enableCluster) {
                            var clientSubOrUnsubMsg = new ClientSubOrUnsubMsg(clientId, 0, null, false, topics, UN_SUB);
                            var im = new InternalMessage<>(clientSubOrUnsubMsg, System.currentTimeMillis(), brokerId);
                            internalMessagePublishService.publish(im, InternalMessageEnum.SUB_UNSUB.getChannel());
                        }
                    });
        }
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
        inMemWildcardTopics.stream()
                .filter(t -> TopicUtils.match(topic, t))
                .forEach(t -> {
                    var clientSubs = inMemTopicClientsMap.get(t);
                    if (!CollectionUtils.isEmpty(clientSubs)) {
                        clientSubList.addAll(clientSubs);
                    }
                });
        if (inMemNoneWildcardTopics.contains(topic)) {
            var clientSubs = inMemTopicClientsMap.get(topic);
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
        for (var topic : inDiskNoneWildcardTopics) {
            if (!authorizedSub.contains(topic)) {
                collect.add(topic);
            }
        }
        for (var topic : inDiskWildcardTopics) {
            if (!authorizedSub.contains(topic)) {
                collect.add(topic);
            }
        }
        for (var topic : inMemNoneWildcardTopics) {
            if (!authorizedSub.contains(topic)) {
                collect.add(topic);
            }
        }
        for (var topic : inMemWildcardTopics) {
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
        final var topic = data.getTopic();
        final var cleanSession = data.isCleanSession();
        switch (type) {
            case SUB -> {
                var clientSub = ClientSub.of(clientId, data.getQos(), topic, cleanSession);
                if (cleanSession) {
                    inMemTopicClientsMap
                            .computeIfAbsent(topic, s -> ConcurrentHashMap.newKeySet())
                            .add(clientSub);
                    if (TopicUtils.isTopicContainWildcard(topic)) {
                        inMemWildcardTopics.add(topic);
                    } else {
                        inMemNoneWildcardTopics.add(topic);
                    }
                    inMemClientTopicsMap
                            .computeIfAbsent(clientId, s -> ConcurrentHashMap.newKeySet())
                            .add(topic);
                } else {
                    if (enableInnerCache) {
                        subscribeWithCache(ClientSub.of(clientId, data.getQos(), topic, false));
                    }
                }
            }
            case UN_SUB -> {
                if (cleanSession) {
                    data.getTopics().forEach(t -> {
                        var clientSubs = inMemTopicClientsMap.get(t);
                        if (!CollectionUtils.isEmpty(clientSubs)) {
                            clientSubs.remove(ClientSub.of(clientId, 0, t, false));
                        }
                    });
                    Optional.ofNullable(inMemClientTopicsMap.get(clientId)).ifPresent(t -> t.removeAll(data.getTopics()));
                } else {
                    unsubscribeWithCache(clientId, data.getTopics())
                            .doOnError(throwable -> log.error(throwable.getMessage(), throwable))
                            .subscribe();
                }
            }
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
                            inDiskWildcardTopics.add(topic);
                        } else {
                            inDiskNoneWildcardTopics.add(topic);
                        }
                    }
                })
                .flatMapIterable(Function.identity())
                .flatMap(topic -> redisTemplate.opsForHash().entries(topicPrefix + topic).map(e -> new Tuple2<>(topic, e)))
                .doOnNext(e -> {
                    var topic = e.t0();
                    var k = (String) e.t1().getKey();
                    var v = (String) e.t1().getValue();
                    inDiskTopicClientsMap.computeIfAbsent(topic, s -> ConcurrentHashMap.newKeySet())
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
        // 1 通配符集合必须都遍历一遍，因为你不确定哪个通配符主题匹配当前主题
        // 2 非通配符集合，先判断是否存在

        // 1 含通配符主题集合
        for (var t : inDiskWildcardTopics) {
            if (TopicUtils.match(topic, t)) {
                var clientSubs = inDiskTopicClientsMap.get(t);
                if (!CollectionUtils.isEmpty(clientSubs)) {
                    clientSubList.addAll(clientSubs);
                }
            }
        }
        for (var t : inMemWildcardTopics) {
            if (TopicUtils.match(topic, t)) {
                var clientSubs = inMemTopicClientsMap.get(t);
                if (!CollectionUtils.isEmpty(clientSubs)) {
                    clientSubList.addAll(clientSubs);
                }
            }
        }

        // 2 不含通配符主题集合
        if (inDiskNoneWildcardTopics.contains(topic)) {
            var clientSubs = inDiskTopicClientsMap.get(topic);
            if (!CollectionUtils.isEmpty(clientSubs)) {
                clientSubList.addAll(clientSubs);
            }
        }
        if (inMemNoneWildcardTopics.contains(topic)) {
            var clientSubs = inMemTopicClientsMap.get(topic);
            if (!CollectionUtils.isEmpty(clientSubs)) {
                clientSubList.addAll(clientSubs);
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
        for (var topic : topics) {
            var clientSubs = inDiskTopicClientsMap.get(topic);
            if (clientSubs != null) {
                clientSubs.remove(ClientSub.of(clientId, 0, topic, false));

                // 判断是否需要移除该主题
                if (clientSubs.isEmpty()) {
                    waitToDel.add(topic);
                }
            }
        }
        if (!waitToDel.isEmpty()) {
            return stringRedisTemplate.opsForSet().remove(topicSetKey, waitToDel.toArray())
                    .doOnSuccess(t -> {
                        // 缓存移除
                        // 注意，不要移除 inMemWildcardTopics, inMemNoneWildcardTopics 中的数据
                        for (var topic : waitToDel) {
                            if (TopicUtils.isTopicContainWildcard(topic)) {
                                inDiskWildcardTopics.remove(topic);
                            } else {
                                inDiskNoneWildcardTopics.remove(topic);
                            }
                        }
                    })
                    .then();
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

        if (TopicUtils.isTopicContainWildcard(topic)) {
            inDiskWildcardTopics.add(topic);
        } else {
            inDiskNoneWildcardTopics.add(topic);
        }

        // 保存客户端订阅内容
        inDiskTopicClientsMap
                .computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet())
                .add(clientSub);
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
