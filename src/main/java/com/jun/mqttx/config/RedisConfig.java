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

package com.jun.mqttx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * value 为 byte[] 类型的通用 redisTemplate
     */
    @Bean
    public ReactiveRedisTemplate<String, byte[]> redisTemplate(ReactiveRedisConnectionFactory redisConnectionFactory) {
        return new ReactiveRedisTemplate<>(redisConnectionFactory,
                new RedisSerializationContext<>() {
                    @Override
                    public SerializationPair<String> getKeySerializationPair() {
                        return SerializationPair.fromSerializer(RedisSerializer.string());
                    }

                    @Override
                    public SerializationPair<byte[]> getValueSerializationPair() {
                        return SerializationPair.byteArray();
                    }

                    @Override
                    public <HK> SerializationPair<HK> getHashKeySerializationPair() {
                        return (SerializationPair<HK>) SerializationPair.fromSerializer(RedisSerializer.string());
                    }

                    @Override
                    public <HV> SerializationPair<HV> getHashValueSerializationPair() {
                        return (SerializationPair<HV>) SerializationPair.byteArray();
                    }

                    @Override
                    public SerializationPair<String> getStringSerializationPair() {
                        return SerializationPair.fromSerializer(RedisSerializer.string());
                    }
                });
    }
}
