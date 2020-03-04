package com.jun.mqttx.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;

/**
 * 消息处理器
 *
 * @author Jun
 * @date 2020-03-03 22:04
 */
public interface MqttMessageHandler {

    /**
     * 处理方法
     *
     * @param ctx 见 {@link ChannelHandlerContext}
     * @param msg 解包后的数据
     */
    void process(ChannelHandlerContext ctx, MqttMessage msg);

    /**
     * 判断报文处理器是否支持处理当前报文类型
     *
     * @return 返回处理器对应的处理类别
     */
    MqttMessageType handleType();
}
