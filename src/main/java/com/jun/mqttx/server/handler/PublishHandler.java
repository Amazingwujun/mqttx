package com.jun.mqttx.server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.springframework.stereotype.Component;

/**
 * {@link MqttMessageType#PUBLISH} 处理器
 *
 * @author Jun
 * @date 2020-03-04 14:30
 */
@Component
public class PublishHandler implements MqttMessageHandler {

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttPublishMessage mpm = (MqttPublishMessage) msg;
        MqttFixedHeader mqttFixedHeader = mpm.fixedHeader();
        MqttPublishVariableHeader mqttPublishVariableHeader = mpm.variableHeader();
        ByteBuf payload = mpm.payload();

        //获取qos、topic、packetId
        MqttQoS mqttQoS = mqttFixedHeader.qosLevel();
        String topic = mqttPublishVariableHeader.topicName();
        int packetId = mqttPublishVariableHeader.packetId();

        //retain 消息处理
        if (mqttFixedHeader.isRetain()) {

        }
    }

    @Override
    public MqttMessageType handleType() {
        return MqttMessageType.PUBLISH;
    }
}
