package com.jun.mqttx.broker.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link MqttMessageType#DISCONNECT} 消息处理器
 *
 * @author Jun
 * @date 2020-03-03 23:30
 */
@Slf4j
@Component
public final class DisconnectHandler extends AbstractMqttSessionHandler {

    private ConnectHandler connectHandler;

    public DisconnectHandler(ConnectHandler connectHandler) {
        this.connectHandler = connectHandler;
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        if (clearSession(ctx)) {
            connectHandler.actionOnCleanSession(clientId(ctx));
        }

        ctx.close();
    }

    @Override
    public MqttMessageType handleType() {
        return MqttMessageType.DISCONNECT;
    }
}
