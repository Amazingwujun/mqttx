package com.jun.mqttx.broker.handler;

import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.service.IPublishMessageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link MqttMessageType#PUBACK} 消息处理器
 *
 * @author Jun
 * @date 2020-03-04 15:59
 */
@Component
public class PubAckHandler extends AbstractMqttMessageHandler {

    private IPublishMessageService publishMessageService;

    public PubAckHandler(StringRedisTemplate stringRedisTemplate, BizConfig bizConfig,
                         IPublishMessageService publishMessageService) {
        super(stringRedisTemplate, bizConfig);
        this.publishMessageService = publishMessageService;
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttPubAckMessage mqttPubAckMessage = (MqttPubAckMessage) msg;
        int messageId = mqttPubAckMessage.variableHeader().messageId();
        publishMessageService.remove(clientId(ctx), messageId);
    }

    @Override
    public MqttMessageType handleType() {
        return MqttMessageType.PUBACK;
    }
}
