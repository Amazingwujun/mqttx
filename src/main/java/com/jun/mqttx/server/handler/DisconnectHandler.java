package com.jun.mqttx.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import org.springframework.stereotype.Component;

/**
 * {@link MqttMessageType#DISCONNECT} 消息处理器
 *
 * @author Jun
 * @date 2020-03-03 23:30
 */
@Component
public final class DisconnectHandler implements MqttMessageHandler {

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {

    }

    @Override
    public MqttMessageType handleType() {
        return MqttMessageType.DISCONNECT;
    }
}
