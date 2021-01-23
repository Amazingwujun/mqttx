package com.jun.mqttx.service.impl;

import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.service.IInternalMessagePublishService;
import com.jun.mqttx.utils.Serializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 基于 Redis 的实现
 *
 * @author Jun
 * @since 1.0.4
 */
@Slf4j
public class DefaultInternalMessagePublishServiceImpl implements IInternalMessagePublishService {

    private final RedisTemplate<String, byte[]> redisTemplate;
    private final Serializer serializer;

    public DefaultInternalMessagePublishServiceImpl(RedisTemplate<String, byte[]> redisTemplate, Serializer serializer) {
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
    }

    @Override
    public <T> void publish(InternalMessage<T> internalMessage, String channel) {
        redisTemplate.convertAndSend(channel, serializer.serialize(internalMessage));
    }
}