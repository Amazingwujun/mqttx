package com.jun.mqttx.broker.handler;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IRetainMessageService;
import com.jun.mqttx.service.ISubscriptionService;
import com.jun.mqttx.utils.TopicUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link MqttMessageType#SUBSCRIBE} 消息处理器
 *
 * @author Jun
 * @since 1.0.4
 */
@Handler(type = MqttMessageType.SUBSCRIBE)
public class SubscribeHandler extends AbstractMqttTopicSecureHandler {

    private final boolean enableTopicPubSubSecure;
    private IRetainMessageService retainMessageService;
    private ISubscriptionService subscriptionService;

    public SubscribeHandler(IRetainMessageService retainMessageService, ISubscriptionService subscriptionService,
                            MqttxConfig mqttxConfig) {
        this.retainMessageService = retainMessageService;
        this.subscriptionService = subscriptionService;
        this.enableTopicPubSubSecure = mqttxConfig.getEnableTopicSubPubSecure();
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        //获取订阅的topic、clientId
        MqttSubscribeMessage mqttSubscribeMessage = (MqttSubscribeMessage) msg;
        int messageId = mqttSubscribeMessage.variableHeader().messageId();
        List<MqttTopicSubscription> mqttTopicSubscriptions = mqttSubscribeMessage.payload().topicSubscriptions();
        String clientId = clientId(ctx);

        //保存用户订阅
        //考虑到某些 topic 的订阅可能不开放给某些 client，针对这些 topic，我们有必要增加权限校验。实现办法有很多，目前的校验机制：
        //当 client 连接并调用认证服务时，认证服务返回 client 具备的哪些 topic 订阅权限，当 enableTopicSubscribeSecure=true 时，
        //程序将校验 client 当前想要订阅的 topic 是否被授权
        List<Integer> grantedQosLevels = new ArrayList<>(mqttTopicSubscriptions.size());
        mqttTopicSubscriptions.forEach(mqttTopicSubscription -> {
            String topic = mqttTopicSubscription.topicName();
            int qos = mqttTopicSubscription.qualityOfService().value();

            if (!TopicUtils.isValid(topic)) {
                //Failure
                qos = 0x80;
            } else {
                if (enableTopicPubSubSecure && !hasAuthToSubTopic(ctx, topic)) {
                    //client 不允许订阅此 topic
                    qos = 0x80;
                } else {
                    ClientSub clientSub = new ClientSub(clientId, qos, topic);
                    subscriptionService.subscribe(clientSub);
                }
            }
            grantedQosLevels.add(qos);
        });

        //acknowledge
        MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId),
                new MqttSubAckPayload(grantedQosLevels));
        ctx.writeAndFlush(mqttMessage);

        //publish retain message with new subscribe
        mqttTopicSubscriptions.forEach(mqttTopicSubscription -> {
            String topic = mqttTopicSubscription.topicName();
            PubMsg pubMsg = retainMessageService.get(topic);

            if (pubMsg != null) {
                MqttPublishMessage mpm = MqttMessageBuilders.publish()
                        .qos(MqttQoS.valueOf(pubMsg.getQoS()))
                        .retained(true)
                        .topicName(topic)
                        .messageId(nextMessageId(ctx))
                        .payload(Unpooled.wrappedBuffer(pubMsg.getPayload()))
                        .build();

                ctx.writeAndFlush(mpm);
            }
        });
    }
}