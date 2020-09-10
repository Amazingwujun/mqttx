package com.jun.mqttx.broker.handler;

import com.jun.mqttx.service.ISubscriptionService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;

/**
 * {@link MqttMessageType#UNSUBSCRIBE} 消息处理器
 *
 * @author Jun
 * @since 1.0.4
 */
@Handler(type = MqttMessageType.UNSUBSCRIBE)
public class UnsubscribeHandler extends AbstractMqttSessionHandler {

    private ISubscriptionService subscriptionService;

    public UnsubscribeHandler(ISubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttUnsubscribeMessage mqttUnsubscribeMessage = (MqttUnsubscribeMessage) msg;
        int messageId = mqttUnsubscribeMessage.variableHeader().messageId();
        MqttUnsubscribePayload payload = mqttUnsubscribeMessage.payload();

        //unsubscribe
        subscriptionService.unsubscribe(clientId(ctx), payload.topics());

        //response
        MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId),
                null
        );
        ctx.writeAndFlush(mqttMessage);
    }
}