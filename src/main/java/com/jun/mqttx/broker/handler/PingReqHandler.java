package com.jun.mqttx.broker.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;

/**
 * {@link MqttMessageType#PINGREQ} 消息处理器
 *
 * @author Jun
 * @since 1.0.4
 */
@Handler(type = MqttMessageType.PINGREQ)
public class PingReqHandler implements MqttMessageHandler {

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0),
                null, null);
        ctx.writeAndFlush(mqttMessage);
    }
}