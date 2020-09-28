package com.jun.mqttx.broker.handler;

import com.jun.mqttx.broker.BrokerHandler;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.constants.InternalMessageEnum;
import com.jun.mqttx.constants.ShareStrategy;
import com.jun.mqttx.consumer.Watcher;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.exception.AuthorizationException;
import com.jun.mqttx.service.*;
import com.jun.mqttx.utils.TopicUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class PublishHandler extends AbstractMqttTopicSecureHandler implements Watcher<PubMsg> {

    private IRetainMessageService retainMessageService;

    private ISubscriptionService subscriptionService;

    private IPublishMessageService publishMessageService;

    private IPubRelMessageService pubRelMessageService;

    private IInternalMessagePublishService internalMessagePublishService;

    private int brokerId;

    private Boolean enableCluster, enableTopicSubPubSecure, enableShareTopic;
    private ShareStrategy shareStrategy;

    /**
     * 共享订阅轮询，存储轮询参数
     */
    private Map<String, AtomicInteger> roundMap;

    public PublishHandler(IPublishMessageService publishMessageService, IRetainMessageService retainMessageService,
                          ISubscriptionService subscriptionService, IPubRelMessageService pubRelMessageService,
                          @Nullable IInternalMessagePublishService internalMessagePublishService, MqttxConfig mqttxConfig) {
        Assert.notNull(publishMessageService, "publishMessageService can't be null");
        Assert.notNull(retainMessageService, "retainMessageService can't be null");
        Assert.notNull(subscriptionService, "publishMessageService can't be null");
        Assert.notNull(pubRelMessageService, "publishMessageService can't be null");
        Assert.notNull(mqttxConfig, "mqttxConfig can't be null");

        MqttxConfig.Cluster cluster = mqttxConfig.getCluster();
        MqttxConfig.ShareTopic shareTopic = mqttxConfig.getShareTopic();
        this.publishMessageService = publishMessageService;
        this.retainMessageService = retainMessageService;
        this.subscriptionService = subscriptionService;
        this.pubRelMessageService = pubRelMessageService;
        this.brokerId = mqttxConfig.getBrokerId();
        this.enableCluster = cluster.getEnable();
        this.enableTopicSubPubSecure = mqttxConfig.getEnableTopicSubPubSecure();
        this.enableShareTopic = shareTopic.getEnable();
        this.shareStrategy = ShareStrategy.getStrategy(shareTopic.getShareSubStrategy());
        if (round == shareStrategy) {
            roundMap = new ConcurrentHashMap<>();
        }

        if (enableCluster) {
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

        // 组装消息
        PubMsg pubMsg = new PubMsg(mqttQoS, packetId, topic, retain, data);

        // 响应
        switch (mqttQoS) {
            case 0: // at most once
                publish(pubMsg, ctx, false);
                break;
            case 1: // at least once
                publish(pubMsg, ctx, false);
                MqttMessage pubAck = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.valueOf(mqttQoS), false, 0),
                        MqttMessageIdVariableHeader.from(packetId),
                        null
                );
                ctx.writeAndFlush(pubAck);
                break;
            case 2: // exactly once
                MqttMessage pubRec = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.valueOf(mqttQoS), false, 0),
                        MqttMessageIdVariableHeader.from(packetId),
                        null
                );
                ctx.writeAndFlush(pubRec);

                // 判断消息是否重复
                if (!pubRelMessageService.isDupMsg(clientId(ctx), packetId)) {
                    // 发布新的消息并保存 pubRel 标记，用于实现Qos2
                    publish(pubMsg, ctx, false);
                    if (isCleanSession(ctx)) {
                        getSession(ctx).savePubRelMsg(packetId);
                    } else {
                        pubRelMessageService.save(clientId(ctx), packetId);
                    }
                }
                break;
        }

        // retain 消息处理
        if (mqttFixedHeader.isRetain()) {
            handleRetainMsg(pubMsg);
        }
    }

    @Override
    public void action(InternalMessage<PubMsg> im) {
        PubMsg data = im.getData();
        publish(data, null, true);
    }

    @Override
    public boolean support(String channel) {
        return InternalMessageEnum.PUB.getChannel().equals(channel);
    }

    /**
     * 消息发布
     *
     * @param pubMsg            publish message
     * @param isInternalMessage 标志消息源是集群还是客户端
     */
    private void publish(final PubMsg pubMsg, ChannelHandlerContext ctx, boolean isInternalMessage) {
        // 获取 topic 订阅者 id 列表
        String topic = pubMsg.getTopic();
        List<ClientSub> clientList = subscriptionService.searchSubscribeClientList(topic);
        if (CollectionUtils.isEmpty(clientList)) {
            return;
        }

        // 共享订阅
        if (enableShareTopic && TopicUtils.isShare(topic)) {
            ClientSub hashClient = chooseClient(clientList, clientId(ctx), topic);
            publish0(ctx, hashClient, pubMsg, isInternalMessage);
            return;
        }

        // 遍历发送
        clientList.forEach(clientSub -> publish0(ctx, clientSub, pubMsg, isInternalMessage));
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
     * 发布消息给 clientSub
     *
     * @param ctx               see {@link ChannelHandlerContext}
     * @param clientSub         {@link ClientSub}
     * @param pubMsg            待发布消息
     * @param isInternalMessage 内部消息flag
     */
    private void publish0(ChannelHandlerContext ctx, ClientSub clientSub, PubMsg pubMsg, boolean isInternalMessage) {
        final String clientId = clientSub.getClientId();
        String topic = pubMsg.getTopic();

        // 计算Qos
        int pubQos = pubMsg.getQoS();
        int subQos = clientSub.getQos();
        MqttQoS qos = subQos >= pubQos ? MqttQoS.valueOf(pubQos) : MqttQoS.valueOf(subQos);

        // 组装PubMsg
        int messageId = nextMessageId(ctx);
        pubMsg.setMessageId(messageId);
        // It MUST set the RETAIN flag to 0 when a PUBLISH Packet is sent to a Client because it matches an established
        // subscription regardless of how the flag was set in the message it received [MQTT-3.3.1-9].
        MqttPublishMessage mpm = MqttMessageBuilders.publish()
                .messageId(messageId)
                .qos(qos)
                .topicName(topic)
                .retained(false)
                .payload(Unpooled.wrappedBuffer(pubMsg.getPayload()))
                .build();

        // 集群消息不做保存，传播消息的 broker 已经保存过了
        if ((qos == MqttQoS.EXACTLY_ONCE || qos == MqttQoS.AT_LEAST_ONCE) && !isInternalMessage) {
            if (isCleanSession(ctx)) {
                // 如果 cleanSession = 1，消息直接关联会话，不需要持久化
                getSession(ctx).savePubMsg(messageId, pubMsg);
            } else {
                publishMessageService.save(clientId, pubMsg);
            }
        }

        // 将消息推送给集群中的broker
        if (enableCluster) {
            internalMessagePublish(pubMsg);
        }

        // 发送
        Optional.of(clientId)
                .map(ConnectHandler.clientMap::get)
                .map(BrokerHandler.CHANNELS::find)
                .ifPresent(channel -> channel.writeAndFlush(mpm));
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
}