package com.jun.mqttx.service.impl;

import com.alibaba.fastjson.JSON;
import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IRetainMessageService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * 存储通过 redis 实现
 *
 * @author Jun
 * @date 2020-03-13 10:48
 */
@Service
public class RetainMessageServiceImpl implements IRetainMessageService {

    private StringRedisTemplate stringRedisTemplate;

    /**
     * redis retain message prefix
     */
    private final String retainMessageHashKey;

    public RetainMessageServiceImpl(StringRedisTemplate stringRedisTemplate, BizConfig bizConfig) {
        Assert.notNull(stringRedisTemplate, "stringRedisTemplate can't be null");

        this.stringRedisTemplate = stringRedisTemplate;
        this.retainMessageHashKey = bizConfig.getRetainMessagePrefix();

        Assert.hasText(retainMessageHashKey, "retainMessagePrefix can't be null");
    }

    @Override
    public void save(String topic, PubMsg pubMsg) {
        stringRedisTemplate.opsForHash().put(retainMessageHashKey, topic, JSON.toJSONString(pubMsg));
    }

    @Override
    public void remove(String topic) {
        stringRedisTemplate.opsForHash().delete(retainMessageHashKey, topic);
    }
}
