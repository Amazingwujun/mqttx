package com.jun.mqttx.broker.handler;

import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.service.ISessionService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link MqttMessageType#DISCONNECT} 消息处理器
 *
 * @author Jun
 * @date 2020-03-03 23:30
 */
@Slf4j
@Component
public final class DisconnectHandler extends AbstractMqttMessageHandler {

    private ISessionService sessionService;

    public DisconnectHandler(StringRedisTemplate stringRedisTemplate, BizConfig bizConfig,
                             ISessionService sessionService) {
        super(stringRedisTemplate, bizConfig);
        this.sessionService = sessionService;
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        //todo clear will Message
        String clientId = (String) ctx.channel().attr(AttributeKey.valueOf("clientId")).get();
        sessionService.clear(clientId);

        ctx.close();
    }

    @Override
    public MqttMessageType handleType() {
        return MqttMessageType.DISCONNECT;
    }
}
