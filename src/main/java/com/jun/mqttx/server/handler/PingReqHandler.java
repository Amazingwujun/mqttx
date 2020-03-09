package com.jun.mqttx.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.springframework.stereotype.Component;

/**
 * {@link MqttMessageType#PINGREQ} 消息处理器
 *
 * @author Jun
 * @date 2020-03-04 16:09
 */
@Component
public class PingReqHandler implements MqttMessageHandler {

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0),
                null, null);
        ctx.writeAndFlush(mqttMessage);
    }

    @Override
    public MqttMessageType handleType() {
        return MqttMessageType.PINGREQ;
    }
}
