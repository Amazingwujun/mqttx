package com.jun.mqttx.service.impl;

import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IPublishMessageService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * publish message store by redis
 *
 * @author Jun
 * @date 2020-03-13 14:33
 */
@Service
public class PublishMessageServiceImpl implements IPublishMessageService {

    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void save(PubMsg pubMsg) {

    }

    @Override
    public void clear(String clientId) {

    }
}
