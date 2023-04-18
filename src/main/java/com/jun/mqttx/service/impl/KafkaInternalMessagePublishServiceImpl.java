/*
 * Copyright 2020-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
