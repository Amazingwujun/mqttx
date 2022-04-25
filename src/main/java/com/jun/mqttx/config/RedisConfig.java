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
