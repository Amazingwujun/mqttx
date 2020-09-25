package com.jun.mqttx.service.impl;

import com.alibaba.fastjson.JSON;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.Session;
import com.jun.mqttx.service.ISessionService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话服务
 *
 * @author Jun
 * @since 1.0.4
 */
@Service
public class SessionServiceImpl implements ISessionService {

    private final String clusterSessionHashKey;
    private StringRedisTemplate stringRedisTemplate;

    private Boolean enableTestMode;
    private Map<String, Session> sessionStore;

    public SessionServiceImpl(StringRedisTemplate stringRedisTemplate, MqttxConfig mqttxConfig) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.clusterSessionHashKey = mqttxConfig.getRedis().getClusterSessionHashKey();

        this.enableTestMode = mqttxConfig.getEnableTestMode();
        if (enableTestMode) {
            sessionStore = new ConcurrentHashMap<>();
        }

        Assert.notNull(stringRedisTemplate, "stringRedisTemplate can't be null");
        Assert.hasText(clusterSessionHashKey, "clusterSessionHashKey can't be null");
    }

    @Override
    public void save(Session session) {
        if (enableTestMode) {
            sessionStore.put(session.getClientId(), session);
            return;
        }

        stringRedisTemplate.opsForHash()
                .put(clusterSessionHashKey, session.getClientId(), JSON.toJSONString(session));
    }

    @Override
    public Session find(String clientId) {
        if (enableTestMode) {
            return sessionStore.get(clientId);
        }

        String sessionStr = (String) stringRedisTemplate.opsForHash().get(clusterSessionHashKey, clientId);
        return Optional.ofNullable(sessionStr)
                .map(e -> JSON.parseObject(e, Session.class))
                .orElse(null);
    }

    @Override
    public void clear(String clientId) {
        if (enableTestMode) {
            sessionStore.remove(clientId);
            return;
        }

        stringRedisTemplate.opsForHash().delete(clusterSessionHashKey, clientId);
    }

    @Override
    public boolean hasKey(String clientId) {
        if (enableTestMode) {
            return sessionStore.containsKey(clientId);
        }

        return stringRedisTemplate.opsForHash().hasKey(clusterSessionHashKey, clientId);
    }
}