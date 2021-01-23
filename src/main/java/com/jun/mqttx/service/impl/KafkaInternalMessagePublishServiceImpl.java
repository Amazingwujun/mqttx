package com.jun.mqttx.service.impl;

import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.service.IInternalMessagePublishService;
import com.jun.mqttx.utils.Serializer;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 基于 kafka 实现
 *
 * @see DefaultInternalMessagePublishServiceImpl
 * @since v1.0.6
 */
public class KafkaInternalMessagePublishServiceImpl implements IInternalMessagePublishService {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final Serializer serializer;

    public KafkaInternalMessagePublishServiceImpl(KafkaTemplate<String, byte[]> kafkaTemplate,
                                                  Serializer serializer) {
        this.kafkaTemplate = kafkaTemplate;
        this.serializer = serializer;
    }

    @Override
    public <T> void publish(InternalMessage<T> internalMessage, String channel) {
        kafkaTemplate.send(channel, serializer.serialize(internalMessage));
    }
}
