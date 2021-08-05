package com.jun.mqttx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * value 为 byte[] 类型的通用 redisTemplate
     */
    @Bean
    public RedisTemplate<String, byte[]> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        //序列化配置
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(RedisSerializer.byteArray());

        return template;
    }

    /**
     * @see #redisTemplate(RedisConnectionFactory)
     */
    @Bean
    public ReactiveRedisTemplate<String, byte[]> reactiveRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {
        RedisSerializationContext.SerializationPair<String> keySerializationPair =
                RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string());
        RedisSerializationContext.SerializationPair<byte[]> valueSerializationPair =
                RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.byteArray());

        RedisSerializationContext<String, byte[]> context = new RedisSerializationContext<String, byte[]>() {
            @Override
            public SerializationPair<String> getKeySerializationPair() {
                return keySerializationPair;
            }

            @Override
            public SerializationPair<byte[]> getValueSerializationPair() {
                return valueSerializationPair;
            }

            @Override
            @SuppressWarnings("unchecked")
            public SerializationPair<String> getHashKeySerializationPair() {
                return keySerializationPair;
            }

            @Override
            @SuppressWarnings("unchecked")
            public SerializationPair<byte[]> getHashValueSerializationPair() {
                return valueSerializationPair;
            }

            @Override
            public SerializationPair<String> getStringSerializationPair() {
                return keySerializationPair;
            }
        };
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
