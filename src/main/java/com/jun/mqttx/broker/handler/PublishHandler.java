package com.jun.mqttx.broker.handler;

import com.alibaba.fastjson.JSON;
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
import com.jun.mqttx.utils.TopicUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final boolean enableTopicSubPubSecure, enableShareTopic;
    /** 共享主题轮询策略 */
    private final ShareStrategy shareStrategy;
    /** 消息桥接开关 */
    private final Boolean enableMessageBridge;
    private IInternalMessagePublishService internalMessagePublishService;
    /** 需要桥接消息的主题 */
    private Set<String> bridgeTopics;
    private KafkaTemplate<String, byte[]> kafkaTemplate;
    /** 共享订阅轮询，存储轮询参数 */
    private Map<String, AtomicInteger> roundMap;

    //@formatter:on

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public PublishHandler(IPublishMessageService publishMessageService, IRetainMessageService retainMessageService,
                          ISubscriptionService subscriptionService, IPubRelMessageService pubRelMessageService, ISessionService sessionService,
                          @Nullable IInternalMessagePublishService internalMessagePublishService, MqttxConfig config,
                          @Nullable KafkaTemplate<String, byte[]> kafkaTemplate, StringRedisTemplate stringRedisTemplate) {
        super(config.getEnableTestMode(), config.getCluster().getEnable());
        Assert.notNull(publishMessageService, "publishMessageService can't be null");
        Assert.notNull(retainMessageService, "retainMessageService can't be null");
        Assert.notNull(subscriptionService, "publishMessageService can't be null");
        Assert.notNull(pubRelMessageService, "publishMessageService can't be null");
        Assert.notNull(config, "mqttxConfig can't be null");
        Assert.notNull(stringRedisTemplate, "stringRedisTemplate can't be null");

        MqttxConfig.ShareTopic shareTopic = config.getShareTopic();
        MqttxConfig.MessageBridge messageBridge = config.getMessageBridge();
        this.sessionService = sessionService;
        this.publishMessageService = publishMessageService;
        this.retainMessageService = retainMessageService;
        this.subscriptionService = subscriptionService;
        this.pubRelMessageService = pubRelMessageService;
        this.brokerId = config.getBrokerId();
        this.enableTopicSubPubSecure = config.getEnableTopicSubPubSecure();
        this.enableShareTopic = shareTopic.getEnable();
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

        // 组装消息
        PubMsg pubMsg = PubMsg.of(mqttQoS, topic, retain, data);

        // 响应
        switch (mqttQoS) {
            case 0: // at most once
                publish(pubMsg, ctx, false);
                break;
            case 1: // at least once
                publish(pubMsg, ctx, false);
                MqttMessage pubAck = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                        MqttMessageIdVariableHeader.from(packetId),
                        null
                );
                ctx.writeAndFlush(pubAck);
                break;
            case 2: // exactly once
                // 判断消息是否重复, 未重复的消息需要保存 messageId
                if (isCleanSession(ctx)) {
                    Session session = getSession(ctx);
                    if (!session.isDupMsg(packetId)) {
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
                break;
        }

        // retain 消息处理
        if (mqttFixedHeader.isRetain()) {
            handleRetainMsg(pubMsg);
        }
    }

    /**
     * 消息发布
     *
     * @param pubMsg           publish message
     * @param isClusterMessage 标志消息源是集群还是客户端
     */
    public void publish(final PubMsg pubMsg, ChannelHandlerContext ctx, boolean isClusterMessage) {
        if (StringUtils.hasText(pubMsg.getAppointedClientId())) {
            // 指定了客户端的消息
            publish0(new ClientSub(pubMsg.getAppointedClientId(), pubMsg.getQoS(), pubMsg.getTopic()), pubMsg, isClusterMessage);
            return;
        }

        // 获取 topic 订阅者 id 列表
        String topic = pubMsg.getTopic();
        List<ClientSub> clientList = subscriptionService.searchSubscribeClientList(topic);
        if (CollectionUtils.isEmpty(clientList)) {
            return;
        }

        // 共享订阅
        if (enableShareTopic && TopicUtils.isShare(topic)) {
            ClientSub luckyClient = chooseClient(clientList, clientId(ctx), topic);
            pubMsg.setAppointedClientId(luckyClient.getClientId());
            publish0(luckyClient, pubMsg, isClusterMessage);

            if (isClusterMode()) {
                internalMessagePublish(pubMsg);
            }
            return;
        }

        // 将消息推送给集群中的broker
        if (isClusterMode() && !isClusterMessage) {
            internalMessagePublish(pubMsg);
        }

        // 遍历发送
        clientList.forEach(clientSub -> publish0(clientSub, pubMsg, isClusterMessage));
    }

    /**
     * 发布消息给 clientSub
     *
     * @param clientSub        {@link ClientSub}
     * @param pubMsg           待发布消息
     * @param isClusterMessage 内部消息flag
     */
    private void publish0(ClientSub clientSub, PubMsg pubMsg, boolean isClusterMessage) {
        // clientId, channel, topic
        final String clientId = clientSub.getClientId();
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
        boolean cleanSession = isCleanSession(clientId);
        if (channel == null && cleanSession) {
            return;
        }

        // 2. channel == null && !cleanSession
        if (channel == null) {
            int messageId = sessionService.nextMessageId(clientId);
            if ((qos == MqttQoS.EXACTLY_ONCE || qos == MqttQoS.AT_LEAST_ONCE) && !isClusterMessage) {
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
        if (cleanSession) {
            messageId = nextMessageId(channel);
            getSession(channel).savePubMsg(messageId, pubMsg);
        } else {
            // 4. channel != null && !cleanSession
            messageId = sessionService.nextMessageId(clientId);
            if ((qos == MqttQoS.EXACTLY_ONCE || qos == MqttQoS.AT_LEAST_ONCE) && !isClusterMessage) {
                pubMsg.setQoS(qos.value());
                pubMsg.setMessageId(messageId);
                publishMessageService.save(clientId, pubMsg);
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
    private void handleRetainMsg(PubMsg pubMsg) {
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
    public void action(String msg) {
        InternalMessage<PubMsg> im = JSON.parseObject(msg, new TypeReference<InternalMessage<PubMsg>>() {
        });
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
     * @param clientId      发送客户端ID
     * @return 按规则选择的客户端
     */
    private ClientSub chooseClient(List<ClientSub> clientSubList, String clientId, String topic) {
        // 集合排序
        clientSubList.sort(ClientSub::compareTo);

        if (hash == shareStrategy) {
            return clientSubList.get(clientId.hashCode() % clientSubList.size());
        } else if (random == shareStrategy) {
            int key = (int) (System.currentTimeMillis() + clientId.hashCode());
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
        return sessionService.hasKey(clientId);
    }
}