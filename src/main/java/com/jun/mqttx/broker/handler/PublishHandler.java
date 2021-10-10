/*
 * Copyright 2002-2020 the original author or authors.
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

package com.jun.mqttx.broker.handler;

import com.alibaba.fastjson.TypeReference;
import com.jun.mqttx.broker.BrokerHandler;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.constants.InternalMessageEnum;
import com.jun.mqttx.constants.ShareStrategy;
import com.jun.mqttx.consumer.Watcher;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.entity.Session;
import com.jun.mqttx.exception.AuthorizationException;
import com.jun.mqttx.service.*;
import com.jun.mqttx.utils.JsonSerializer;
import com.jun.mqttx.utils.RateLimiter;
import com.jun.mqttx.utils.Serializer;
import com.jun.mqttx.utils.TopicUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.jun.mqttx.constants.ShareStrategy.*;

/**
 * {@link MqttMessageType#PUBLISH} 处理器
 *
 * @author Jun
 * @since 1.0.4
 */
@Handler(type = MqttMessageType.PUBLISH)
public class PublishHandler extends AbstractMqttTopicSecureHandler implements Watcher {
    //@formatter:off

    private final ISessionService sessionService;
    private final IRetainMessageService retainMessageService;
    private final ISubscriptionService subscriptionService;
    private final IPublishMessageService publishMessageService;
    private final IPubRelMessageService pubRelMessageService;
    private final int brokerId;
    private final boolean enableTopicSubPubSecure, enableShareTopic, enableRateLimiter, ignoreClientSelfPub;
    /** 共享主题轮询策略 */
    private final ShareStrategy shareStrategy;
    /** 消息桥接开关 */
    private final Boolean enableMessageBridge;
    /** 主题限流器 */
    private final Map<String, RateLimiter> rateLimiterMap = new HashMap<>();
    private final Serializer serializer;
    private IInternalMessagePublishService internalMessagePublishService;
    /** 需要桥接消息的主题 */
    private Set<String> bridgeTopics;
    private KafkaTemplate<String, byte[]> kafkaTemplate;
    /** 共享订阅轮询，存储轮询参数 */
    private Map<String, AtomicInteger> roundMap;

    //@formatter:on

    public PublishHandler(IPublishMessageService publishMessageService, IRetainMessageService retainMessageService,
                          ISubscriptionService subscriptionService, IPubRelMessageService pubRelMessageService, ISessionService sessionService,
                          @Nullable IInternalMessagePublishService internalMessagePublishService, MqttxConfig config,
                          @Nullable KafkaTemplate<String, byte[]> kafkaTemplate, Serializer serializer) {
        super(config.getEnableTestMode(), config.getCluster().getEnable());
        Assert.notNull(publishMessageService, "publishMessageService can't be null");
        Assert.notNull(retainMessageService, "retainMessageService can't be null");
        Assert.notNull(subscriptionService, "publishMessageService can't be null");
        Assert.notNull(pubRelMessageService, "publishMessageService can't be null");
        Assert.notNull(config, "mqttxConfig can't be null");

        MqttxConfig.ShareTopic shareTopic = config.getShareTopic();
        MqttxConfig.MessageBridge messageBridge = config.getMessageBridge();
        MqttxConfig.RateLimiter rateLimiter = config.getRateLimiter();
        this.sessionService = sessionService;
        this.serializer = serializer;
        this.publishMessageService = publishMessageService;
        this.retainMessageService = retainMessageService;
        this.subscriptionService = subscriptionService;
        this.pubRelMessageService = pubRelMessageService;
        this.brokerId = config.getBrokerId();
        this.enableTopicSubPubSecure = config.getEnableTopicSubPubSecure();
        this.ignoreClientSelfPub = config.getIgnoreClientSelfPub();
        this.enableShareTopic = shareTopic.getEnable();
        if (!CollectionUtils.isEmpty(rateLimiter.getTopicRateLimits()) && rateLimiter.getEnable()) {
            enableRateLimiter = true;
            rateLimiter.getTopicRateLimits()
                    .forEach(
                            topicRateLimit -> rateLimiterMap.put(
                                    topicRateLimit.getTopic(),
                                    new RateLimiter(topicRateLimit.getCapacity(), topicRateLimit.getReplenishRate(), topicRateLimit.getTokenConsumedPerAcquire())
                            )
                    );
        } else {
            enableRateLimiter = false;
        }
        this.shareStrategy = shareTopic.getShareSubStrategy();
        if (round == shareStrategy) {
            roundMap = new ConcurrentHashMap<>();
        }
        this.enableMessageBridge = messageBridge.getEnable();
        if (enableMessageBridge) {
            this.bridgeTopics = messageBridge.getTopics();
            this.kafkaTemplate = kafkaTemplate;

            Assert.notEmpty(bridgeTopics, "消息桥接主题列表不能为空!!!");
        }

        if (isClusterMode()) {
            this.internalMessagePublishService = internalMessagePublishService;
            Assert.notNull(internalMessagePublishService, "internalMessagePublishService can't be null");
        }
    }

    /**
     * 根据 MQTT v3.1.1 Qos2 实现有 Method A 与 Method B,这里采用 B 方案，
     * 具体参见 <b>Figure 4.3-Qos protocol flow diagram,non normative example</b>
     *
     * @param ctx 见 {@link ChannelHandlerContext}
     * @param msg 解包后的数据
     */
    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttPublishMessage mpm = (MqttPublishMessage) msg;
        MqttFixedHeader mqttFixedHeader = mpm.fixedHeader();
        MqttPublishVariableHeader mqttPublishVariableHeader = mpm.variableHeader();
        ByteBuf payload = mpm.payload();

        // 获取qos、topic、packetId、retain、payload
        int mqttQoS = mqttFixedHeader.qosLevel().value();
        String topic = mqttPublishVariableHeader.topicName();
        int packetId = mqttPublishVariableHeader.packetId();
        boolean retain = mqttFixedHeader.isRetain();
        byte[] data = new byte[payload.readableBytes()];
        payload.readBytes(data);

        // 发布权限判定
        if (enableTopicSubPubSecure && !hasAuthToPubTopic(ctx, topic)) {
            throw new AuthorizationException("无对应 topic 发布权限");
        }

        // 消息桥接功能，便于对接各类 MQ(kafka, RocketMQ).
        // 这里提供 kafka 的实现，需要对接其它 MQ 的同学可自行修改.
        if (enableMessageBridge && bridgeTopics.contains(topic)) {
            kafkaTemplate.send(topic, data);
        }

        // 限流判定, 满足如下四个条件即被限流：
        // 1 限流器开启
        // 2 qos = 0
        // 3 该主题配置了限流器
        // 4 令牌获取失败
        // 被限流的消息就会被直接丢弃
        if (enableRateLimiter &&
                mqttQoS == MqttQoS.AT_MOST_ONCE.value() &&
                rateLimiterMap.containsKey(topic) &&
                !rateLimiterMap.get(topic).acquire(Instant.now().getEpochSecond())) {
            return;
        }

        // 组装消息
        PubMsg pubMsg = PubMsg.of(mqttQoS, topic, retain, data);

        // 响应
        switch (mqttQoS) {
            case 0 -> // at most once
                    publish(pubMsg, ctx, false);
            case 1 -> { // at least once
                publish(pubMsg, ctx, false);
                MqttMessage pubAck = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                        MqttMessageIdVariableHeader.from(packetId),
                        null
                );
                ctx.writeAndFlush(pubAck);
            }
            case 2 -> { // exactly once
                // 判断消息是否重复, 未重复的消息需要保存 messageId
                if (isCleanSession(ctx)) {
                    Session session = getSession(ctx);
                    if (!session.isDupMsg(packetId)) {
                        publish(pubMsg, ctx, false);
                        session.savePubRelInMsg(packetId);
                    }
                } else {
                    if (!pubRelMessageService.isInMsgDup(clientId(ctx), packetId)) {
                        publish(pubMsg, ctx, false);
                        pubRelMessageService.saveIn(clientId(ctx), packetId);
                    }
                }
                MqttMessage pubRec = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0),
                        MqttMessageIdVariableHeader.from(packetId),
                        null
                );
                ctx.writeAndFlush(pubRec);
            }
        }

        // retain 消息处理
        if (mqttFixedHeader.isRetain()) {
            handleRetainMsg(pubMsg);
        }
    }

    /**
     * 消息发布，目前看来 {@link PubMsg} 的来源有如下几种：
     * <ol>
     *     <li>{@link MqttMessageType#PUBLISH} 消息</li>
     *     <li>遗嘱消息</li>
     *     <li>retain 消息被新订阅触发 </li>
     *     <li>集群消息 {@link #action(byte[])}</li>
     * </ol>
     *
     * @param pubMsg           publish message
     * @param ctx              {@link ChannelHandlerContext}, 该上下文应该是发送消息 client 的上下文
     * @param isClusterMessage 标志消息源是集群还是客户端
     */
    public void publish(final PubMsg pubMsg, ChannelHandlerContext ctx, boolean isClusterMessage) {
        // 指定了客户端的消息
        if (StringUtils.hasText(pubMsg.getAppointedClientId())) {
            final String clientId = pubMsg.getAppointedClientId();
            if (isClusterMessage) {
                // 集群内共享主题分发消息; 因为消息由集群中其它 broker 分发, 所以 cleanSession  的判断无法直接调用
                // isCleanSession(ChannelHandlerContext ctx).
                publish0(ClientSub.of(clientId, pubMsg.getQoS(), pubMsg.getTopic(), isCleanSession(clientId)), pubMsg, true);
            } else {
                // 新订阅触发 retain 消息, 见 SubscribeHandler 中关于 retain 消息的处理逻辑.
                // 此时 ctx 为发送订阅消息 Client 的上下文
                publish0(ClientSub.of(clientId, pubMsg.getQoS(), pubMsg.getTopic(), isCleanSession(ctx)), pubMsg, false);
            }
            return;
        }

        // 获取 topic 订阅者 id 列表
        String topic = pubMsg.getTopic();
        List<ClientSub> clientList = subscriptionService.searchSubscribeClientList(topic);
        if (CollectionUtils.isEmpty(clientList)) {
            return;
        }

        // 忽略 client 自身的订阅
        if (ignoreClientSelfPub) {
            clientList = clientList
                    .stream()
                    .filter(clientSub -> !Objects.equals(clientSub.getClientId(), clientId(ctx)))
                    .collect(Collectors.toList());
        }

        // 共享订阅
        if (enableShareTopic && TopicUtils.isShare(topic)) {
            ClientSub luckyClient = chooseClient(clientList, topic);
            pubMsg.setAppointedClientId(luckyClient.getClientId());
            publish0(luckyClient, pubMsg, isClusterMessage);

            // 满足如下条件，则发送消息给集群
            // 1 集群模式开启
            // 2 订阅的客户端连接在其它实例上
            if (isClusterMode() && !ConnectHandler.CLIENT_MAP.containsKey(luckyClient.getClientId())) {
                internalMessagePublish(pubMsg);
            }
            return;
        }

        // 将消息推送给集群中的broker
        if (isClusterMode() && !isClusterMessage) {
            // 判断是否需要进行集群消息分发
            boolean flag = false;
            for (ClientSub clientSub : clientList) {
                if (!ConnectHandler.CLIENT_MAP.containsKey(clientSub.getClientId())) {
                    flag = true;
                    break;
                }
            }
            if (flag) {
                internalMessagePublish(pubMsg);
            }
        }

        // 遍历发送
        clientList.forEach(clientSub -> publish0(clientSub, pubMsg, isClusterMessage));
    }

    /**
     * 发布消息给 clientSub
     *
     * @param clientSub        {@link ClientSub}
     * @param pubMsg           待发布消息
     * @param isClusterMessage 内部消息flag，设计上由其它集群分发过来的消息
     */
    private void publish0(ClientSub clientSub, PubMsg pubMsg, boolean isClusterMessage) {
        // clientId, channel, topic
        final String clientId = clientSub.getClientId();
        final boolean isCleanSession = clientSub.isCleanSession();
        Channel channel = Optional.of(clientId)
                .map(ConnectHandler.CLIENT_MAP::get)
                .map(BrokerHandler.CHANNELS::find)
                .orElse(null);
        String topic = pubMsg.getTopic();

        // 计算Qos
        int pubQos = pubMsg.getQoS();
        int subQos = clientSub.getQos();
        MqttQoS qos = subQos >= pubQos ? MqttQoS.valueOf(pubQos) : MqttQoS.valueOf(subQos);
        byte[] payload = pubMsg.getPayload();
        boolean isDup = pubMsg.isDup();

        // 接下来的处理分四种情况
        // 1. channel == null && cleanSession  => 直接返回，由集群中其它的 broker 处理（pubMsg 无 messageId）
        // 2. channel == null && !cleanSession => 保存 pubMsg （pubMsg 有 messageId）
        // 3. channel != null && cleanSession  => 将消息关联到会话，并发送 publish message 给 client（messageId 取自 session）
        // 4. channel != null && !cleanSession => 将消息持久化到 redis, 并发送 publish message 给 client（messageId redis increment 指令）

        // 1. channel == null && cleanSession
        if (channel == null && isCleanSession) {
            return;
        }

        // 2. channel == null && !cleanSession
        if (channel == null) {
            if ((qos == MqttQoS.EXACTLY_ONCE || qos == MqttQoS.AT_LEAST_ONCE) && !isClusterMessage) {
                int messageId = sessionService.nextMessageId(clientId);
                pubMsg.setQoS(qos.value());
                pubMsg.setMessageId(messageId);
                publishMessageService.save(clientId, pubMsg);
            }
            return;
        }

        // 处理 channel != null 的情况
        // 计算 messageId
        int messageId;

        // 3. channel != null && cleanSession
        if (isCleanSession) {
            // cleanSession 状态下不判断消息是否为集群
            // 假设消息由集群内其它 broker 分发，而 cleanSession 状态下 broker 消息走的内存，为了实现 qos1,2 我们必须将消息保存到内存
            if ((qos == MqttQoS.EXACTLY_ONCE || qos == MqttQoS.AT_LEAST_ONCE)) {
                messageId = nextMessageId(channel);
                getSession(channel).savePubMsg(messageId, pubMsg);
            } else {
                // qos0
                messageId = 0;
            }
        } else {
            // 4. channel != null && !cleanSession
            if (qos == MqttQoS.EXACTLY_ONCE || qos == MqttQoS.AT_LEAST_ONCE) {
                messageId = sessionService.nextMessageId(clientId);
                if (!isClusterMessage) {
                    pubMsg.setQoS(qos.value());
                    pubMsg.setMessageId(messageId);
                    publishMessageService.save(clientId, pubMsg);
                }
            } else {
                // qos0
                messageId = 0;
            }
        }

        // It MUST set the RETAIN flag to 0 when a PUBLISH Packet is sent to a Client because it matches an established
        // subscription regardless of how the flag was set in the message it received [MQTT-3.3.1-9].
        // 发送报文给 client
        MqttPublishMessage mpm = new MqttPublishMessage(
                new MqttFixedHeader(MqttMessageType.PUBLISH, isDup, qos, false, 0),
                new MqttPublishVariableHeader(topic, messageId),
                Unpooled.wrappedBuffer(payload));

        channel.writeAndFlush(mpm);
    }

    /**
     * 处理 retain 消息
     *
     * @param pubMsg retain message
     */
    public void handleRetainMsg(PubMsg pubMsg) {
        byte[] payload = pubMsg.getPayload();
        String topic = pubMsg.getTopic();
        int qos = pubMsg.getQoS();

        // 如果 retain = 1 且 payload bytes.size = 0
        if (payload == null || payload.length == 0) {
            subscriptionService.removeTopic(topic);
            return;
        }

        // 如果 qos = 0 且  retain = 1
        if (MqttQoS.AT_MOST_ONCE.value() == qos) {
            retainMessageService.remove(topic);
            return;
        }

        retainMessageService.save(topic, pubMsg);
    }

    /**
     * 集群内部消息发布
     *
     * @param pubMsg {@link PubMsg}
     */
    private void internalMessagePublish(PubMsg pubMsg) {
        InternalMessage<PubMsg> im = new InternalMessage<>(pubMsg, System.currentTimeMillis(), brokerId);
        internalMessagePublishService.publish(im, InternalMessageEnum.PUB.getChannel());
    }

    @Override
    public void action(byte[] msg) {
        InternalMessage<PubMsg> im;
        if (serializer instanceof JsonSerializer s) {
            im = s.deserialize(msg, new TypeReference<>() {
            });
        } else {
            //noinspection unchecked
            im = serializer.deserialize(msg, InternalMessage.class);
        }
        PubMsg data = im.getData();
        publish(data, null, true);
    }

    @Override
    public boolean support(String channel) {
        return InternalMessageEnum.PUB.getChannel().equals(channel);
    }

    /**
     * 共享订阅选择客户端, 支持的策略如下：
     * <ol>
     *     <li>随机: {@link ShareStrategy#random}</li>
     *     <li>哈希: {@link ShareStrategy#hash}</li>
     *     <li>轮询: {@link ShareStrategy#round}</li>
     * </ol>
     *
     * @param clientSubList 接收客户端列表
     * @return 按规则选择的客户端
     */
    private ClientSub chooseClient(List<ClientSub> clientSubList, String topic) {
        // 集合排序
        clientSubList.sort(ClientSub::compareTo);

        if (hash == shareStrategy) {
            return clientSubList.get(topic.hashCode() % clientSubList.size());
        } else if (random == shareStrategy) {
            int key = ThreadLocalRandom.current().nextInt(0, clientSubList.size());
            return clientSubList.get(key % clientSubList.size());
        } else if (round == shareStrategy) {
            int i = roundMap.computeIfAbsent(topic, s -> new AtomicInteger(0)).getAndIncrement();
            return clientSubList.get(i % clientSubList.size());
        }

        throw new IllegalArgumentException("不可能到达的代码, strategy:" + shareStrategy);
    }

    /**
     * 判断 clientId 关联的会话是否是 cleanSession 会话
     *
     * @param clientId 客户端id
     * @return true if session is cleanSession
     */
    private boolean isCleanSession(String clientId) {
        return !sessionService.hasKey(clientId);
    }
}
