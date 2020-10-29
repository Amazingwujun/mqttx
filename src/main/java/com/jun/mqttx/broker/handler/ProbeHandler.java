package com.jun.mqttx.broker.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据探针, 目前功能：
 * <ul>
 *     <li>计算收到的 {@link MqttMessage} 总数</li>
 *     <li>计算发送的 {@link MqttMessage} 总数</li>
 * </ul>
 *
 * @author Jun
 * @since 1.0.6
 */
@Component
@ConditionalOnProperty(value = "mqttx.sys-topic.enable", havingValue = "true")
@ChannelHandler.Sharable
public class ProbeHandler extends ChannelDuplexHandler {
    //@formatter:off

    /** 自代理启动以来接收的 {@link MqttMessage} 数量 */
    public static final AtomicLong IN_MSG_SIZE = new AtomicLong(0);
    /** 自代理启动以来发送的 {@link MqttMessage} 数量 */
    public static final AtomicLong OUT_MSG_SIZE = new AtomicLong(0);

    //@formatter:on

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        handleEvent(msg, IN_MSG_SIZE);

        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        handleEvent(msg, OUT_MSG_SIZE);

        ctx.write(msg, promise);
    }

    private void handleEvent(Object msg, AtomicLong mark) {
        if (msg instanceof MqttMessage && ((MqttMessage) msg).decoderResult().isSuccess()) {
            MqttMessageType messageType = ((MqttMessage) msg).fixedHeader().messageType();
            // 忽略心跳
            if (MqttMessageType.PINGREQ == messageType || MqttMessageType.PINGRESP == messageType) {
                return;
            }
            mark.incrementAndGet();
        }
    }
}
