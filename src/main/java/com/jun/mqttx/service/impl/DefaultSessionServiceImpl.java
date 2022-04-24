package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.Session;
import com.jun.mqttx.service.ISessionService;
import com.jun.mqttx.utils.Serializer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Optional;

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
    private final RedisTemplate<String, byte[]> redisTemplate;
    private final Serializer serializer;

    public DefaultSessionServiceImpl(RedisTemplate<String, byte[]> redisTemplate,
                                     Serializer serializer,
                                     MqttxConfig mqttxConfig) {
        MqttxConfig.Redis redis = mqttxConfig.getRedis();
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
        this.clusterSessionHashKey = redis.getClusterSessionHashKey();
        this.messageIdPrefix = redis.getMessageIdPrefix();

        Assert.notNull(redisTemplate, "stringRedisTemplate can't be null");
        Assert.hasText(clusterSessionHashKey, "clusterSessionHashKey can't be null");
    }

    @Override
    public void save(Session session) {
        redisTemplate.opsForHash()
                .put(clusterSessionHashKey, session.getClientId(), serializer.serialize(session));
    }

    @Override
    public Session find(String clientId) {
        byte[] sessionStr = (byte[]) redisTemplate.opsForHash().get(clusterSessionHashKey, clientId);
        return Optional.ofNullable(sessionStr)
                .map(e -> serializer.deserialize(e, Session.class))
                .orElse(null);
    }

    @Override
    public boolean clear(String clientId) {
        redisTemplate.delete(messageIdPrefix + clientId);
        return redisTemplate.opsForHash().delete(clusterSessionHashKey, clientId) > 0;
    }

    @Override
    public boolean hasKey(String clientId) {
        return redisTemplate.opsForHash().hasKey(clusterSessionHashKey, clientId);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public int nextMessageId(String clientId) {
        int messageId = Math.toIntExact(redisTemplate.opsForValue().increment(messageIdPrefix + clientId));
        if (messageId == 0) {
            messageId = Math.toIntExact(redisTemplate.opsForValue().increment(messageIdPrefix + clientId));
        }

        return messageId;
    }
}
