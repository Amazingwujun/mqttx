package com.jun.mqttx.service.impl;

import com.alibaba.fastjson.JSON;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.Session;
import com.jun.mqttx.service.ISessionService;
import com.jun.mqttx.utils.MessageIdUtils;
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
public class DefaultSessionServiceImpl implements ISessionService {

    private final String clusterSessionHashKey;
    private final String messageIdPrefix;
    private final StringRedisTemplate stringRedisTemplate;

    private final boolean enableTestMode;
    private Map<String, Session> sessionStore;

    public DefaultSessionServiceImpl(StringRedisTemplate stringRedisTemplate, MqttxConfig mqttxConfig) {
        MqttxConfig.Redis redis = mqttxConfig.getRedis();
        this.stringRedisTemplate = stringRedisTemplate;
        this.clusterSessionHashKey = redis.getClusterSessionHashKey();
        this.messageIdPrefix = redis.getMessageIdPrefix();

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
        stringRedisTemplate.delete(messageIdPrefix + clientId);
    }

    @Override
    public boolean hasKey(String clientId) {
        if (enableTestMode) {
            return sessionStore.containsKey(clientId);
        }

        return stringRedisTemplate.opsForHash().hasKey(clusterSessionHashKey, clientId);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public int nextMessageId(String clientId) {
        if (enableTestMode) {
            return MessageIdUtils.nextMessageId(clientId);
        }
        int messageId = Math.toIntExact(stringRedisTemplate.opsForValue().increment(messageIdPrefix + clientId));
        if (messageId == 0) {
            messageId = Math.toIntExact(stringRedisTemplate.opsForValue().increment(messageIdPrefix + clientId));
        }

        return messageId;
    }
}