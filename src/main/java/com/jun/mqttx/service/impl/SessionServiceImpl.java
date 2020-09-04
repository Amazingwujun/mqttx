package com.jun.mqttx.service.impl;

import com.alibaba.fastjson.JSON;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.Session;
import com.jun.mqttx.service.ISessionService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Optional;

/**
 * 会话服务
 *
 * @author Jun
 * @date 2020-03-04 14:01
 */
@Service
public class SessionServiceImpl implements ISessionService {

    private final String clusterSessionHashKey;
    private StringRedisTemplate stringRedisTemplate;

    public SessionServiceImpl(StringRedisTemplate stringRedisTemplate, MqttxConfig mqttxConfig) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.clusterSessionHashKey = mqttxConfig.getRedis().getClusterSessionHashKey();

        Assert.notNull(stringRedisTemplate, "stringRedisTemplate can't be null");
        Assert.hasText(clusterSessionHashKey, "clusterSessionHashKey can't be null");
    }

    @Override
    public void save(Session session) {
        stringRedisTemplate.opsForHash()
                .put(clusterSessionHashKey, session.getClientId(), JSON.toJSONString(session));
    }

    @Override
    public Session find(String clientId) {
        String sessionStr = (String) stringRedisTemplate.opsForHash().get(clusterSessionHashKey, clientId);
        return Optional.ofNullable(sessionStr)
                .map(e -> JSON.parseObject(e, Session.class))
                .orElse(null);
    }

    @Override
    public void clear(String clientId) {
        stringRedisTemplate.opsForHash().delete(clusterSessionHashKey, clientId);
    }

    @Override
    public boolean hasKey(String clientId) {
        return stringRedisTemplate.opsForHash().hasKey(clusterSessionHashKey, clientId);
    }
}
