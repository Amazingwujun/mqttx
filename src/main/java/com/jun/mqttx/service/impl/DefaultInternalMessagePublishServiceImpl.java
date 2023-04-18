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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 基于 Redis 的实现
 *
 * @author Jun
 * @since 1.0.4
 */
@Slf4j
public class DefaultInternalMessagePublishServiceImpl implements IInternalMessagePublishService {

    private final ReactiveRedisTemplate<String, byte[]> redisTemplate;
    private final Serializer serializer;

    public DefaultInternalMessagePublishServiceImpl(ReactiveRedisTemplate<String, byte[]> redisTemplate, Serializer serializer) {
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
    }

    @Override
    public <T> void publish(InternalMessage<T> internalMessage, String channel) {
        redisTemplate.convertAndSend(channel, serializer.serialize(internalMessage)).subscribe();
    }
}
