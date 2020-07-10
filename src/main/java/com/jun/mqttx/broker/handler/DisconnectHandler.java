package com.jun.mqttx.broker.handler;

import com.jun.mqttx.broker.BrokerHandler;
import com.jun.mqttx.common.constant.InternalMessageEnum;
import com.jun.mqttx.consumer.Watcher;
import com.jun.mqttx.entity.InternalMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link MqttMessageType#DISCONNECT} 消息处理器
 *
 * @author Jun
 * @date 2020-03-03 23:30
 */
@Slf4j
@Handler(type = MqttMessageType.DISCONNECT)
public final class DisconnectHandler extends AbstractMqttSessionHandler implements Watcher<String> {

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
    public void action(InternalMessage<String> im) {
        ChannelId channelId = ConnectHandler.clientMap.get(im.getData());
        if (channelId != null) {
            BrokerHandler.channels.find(channelId).close();
        }
    }

    @Override
    public boolean support(String channel) {
        return InternalMessageEnum.DISCONNECT.getChannel().equals(channel);
    }
}
