package com.jun.mqttx.service.impl;

import com.alibaba.fastjson.JSON;
import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.service.IInternalMessagePublishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * 基于 Redis 的实现
 *
 * @author Jun
 * @since 1.0.4
 */
@Slf4j
public class InternalMessagePublishServiceImpl implements IInternalMessagePublishService {

    private StringRedisTemplate stringRedisTemplate;

    private ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    public InternalMessagePublishServiceImpl(StringRedisTemplate stringRedisTemplate,
                                             ReactiveStringRedisTemplate reactiveStringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
    }

    @Override
    public <T> void publish(InternalMessage<T> internalMessage, String channel) {
        stringRedisTemplate.convertAndSend(channel, JSON.toJSONString(internalMessage));
    }

    @Override
    public <T> Mono<Long> asyncPublish(InternalMessage<T> internalMessage, String channel) {
        return reactiveStringRedisTemplate.convertAndSend(channel, JSON.toJSONString(internalMessage));
    }
}