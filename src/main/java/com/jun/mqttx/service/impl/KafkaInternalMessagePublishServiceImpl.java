package com.jun.mqttx.service.impl;

import com.alibaba.fastjson.JSON;
import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.service.IInternalMessagePublishService;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 基于 kafka 实现
 *
 * @see DefaultInternalMessagePublishServiceImpl
 * @since v1.0.6
 */
public class KafkaInternalMessagePublishServiceImpl implements IInternalMessagePublishService {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public KafkaInternalMessagePublishServiceImpl(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public <T> void publish(InternalMessage<T> internalMessage, String channel) {
        kafkaTemplate.send(channel, JSON.toJSONBytes(internalMessage));
    }
}
