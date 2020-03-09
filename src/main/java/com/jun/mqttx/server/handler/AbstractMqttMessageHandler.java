package com.jun.mqttx.server.handler;

import com.jun.mqttx.common.config.BizConfig;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.Assert;

/**
 * 部分通用方法
 *
 * @author Jun
 * @date 2020-03-07 22:20
 */
public abstract class AbstractMqttMessageHandler implements MqttMessageHandler {

    private StringRedisTemplate stringRedisTemplate;

    private String messageIdPrefix;

    public AbstractMqttMessageHandler(StringRedisTemplate stringRedisTemplate, BizConfig bizConfig) {
        Assert.notNull(stringRedisTemplate, "stringRedisTemplate can't be null");

        this.stringRedisTemplate = stringRedisTemplate;
        this.messageIdPrefix = bizConfig.getMessageIdPrefix();

        Assert.hasText(messageIdPrefix, "messageIdPrefix can't be null");
    }

    /**
     * 生成消息ID
     *
     * @param clientId 客户端identifier
     * @return 消息ID
     */
    int nextMessageId(String clientId) {
        return stringRedisTemplate.opsForValue()
                .increment(messageIdPrefix + clientId).intValue();
    }
}
