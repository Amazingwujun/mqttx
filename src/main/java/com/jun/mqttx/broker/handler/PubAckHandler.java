package com.jun.mqttx.broker.handler;

import com.jun.mqttx.service.IPublishMessageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;

/**
 * {@link MqttMessageType#PUBACK} 消息处理器
 *
 * @author Jun
 * @since 1.0.4
 */
@Handler(type = MqttMessageType.PUBACK)
public class PubAckHandler extends AbstractMqttSessionHandler {

    private IPublishMessageService publishMessageService;

    public PubAckHandler(IPublishMessageService publishMessageService) {
        this.publishMessageService = publishMessageService;
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttPubAckMessage mqttPubAckMessage = (MqttPubAckMessage) msg;
        int messageId = mqttPubAckMessage.variableHeader().messageId();
        if (isCleanSession(ctx)) {
            getSession(ctx).removePubMsg(messageId);
        } else {
            publishMessageService.remove(clientId(ctx), messageId);
        }
    }
}