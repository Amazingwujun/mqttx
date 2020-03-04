package com.jun.mqttx.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import org.springframework.stereotype.Component;

/**
 * {@link MqttMessageType#PUBACK} 消息处理器
 *
 * @author Jun
 * @date 2020-03-04 15:59
 */
@Component
public class PubAckHandler implements MqttMessageHandler {

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {

    }

    @Override
    public MqttMessageType handleType() {
        return MqttMessageType.PUBACK;
    }
}
