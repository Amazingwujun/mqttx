package com.jun.mqttx.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 消息处理器
 *
 * @author Jun
 * @date 2020-03-03 22:00
 */
@Component
public class MessageDelegatingHandler {

    /**
     * 服务端需要处理的消息类别共10种：
     * <ol>
     *     <li>{@link MqttMessageType#CONNECT}</li>
     *     <li>{@link MqttMessageType#PUBLISH}</li>
     *     <li>{@link MqttMessageType#PUBACK}</li>
     *     <li>{@link MqttMessageType#PUBREC}</li>
     *     <li>{@link MqttMessageType#PUBREC}</li>
     *     <li>{@link MqttMessageType#PUBCOMP}</li>
     *     <li>{@link MqttMessageType#SUBSCRIBE}</li>
     *     <li>{@link MqttMessageType#UNSUBSCRIBE}</li>
     *     <li>{@link MqttMessageType#PINGREQ}</li>
     *     <li>{@link MqttMessageType#DISCONNECT}</li>
     * </ol>
     */
    private final Map<MqttMessageType, MqttMessageHandler> handlerMap = new HashMap<>(10);

    /**
     * 将处理器置入 {@link #handlerMap}
     *
     * @param mqttMessageHandlers 消息处理器
     */
    public MessageDelegatingHandler(List<MqttMessageHandler> mqttMessageHandlers) {
        Assert.notEmpty(mqttMessageHandlers, "messageHandlers can't be empty");

        //置入处理器
        mqttMessageHandlers.forEach(mqttMessageHandler -> handlerMap.put(mqttMessageHandler.handleType(), mqttMessageHandler));
    }

    /**
     * 将消息委派给真正的 {@link MqttMessageHandler}
     *
     * @param ctx         {@link ChannelHandlerContext}
     * @param mqttMessage {@link MqttMessageType}
     */
    public void handle(ChannelHandlerContext ctx, MqttMessage mqttMessage) {
        MqttMessageType mqttMessageType = mqttMessage.fixedHeader().messageType();
        Optional.ofNullable(handlerMap.get(mqttMessageType))
                .ifPresent(mqttMessageHandler -> mqttMessageHandler.process(ctx, mqttMessage));
    }
}
