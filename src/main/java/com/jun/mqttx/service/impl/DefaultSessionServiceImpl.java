package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.Session;
import com.jun.mqttx.service.ISessionService;
import com.jun.mqttx.utils.Serializer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.util.Objects;

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
    private final ReactiveRedisTemplate<String, byte[]> redisTemplate;
    private final Serializer serializer;

    public DefaultSessionServiceImpl(ReactiveRedisTemplate<String, byte[]> redisTemplate,
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
    public Mono<Void> save(Session session) {
        return redisTemplate.opsForHash()
                .put(clusterSessionHashKey, session.getClientId(), serializer.serialize(session))
                .then(Mono.empty());
    }

    @Override
    public Mono<Session> find(String clientId) {
        return redisTemplate.opsForHash().get(clusterSessionHashKey, clientId)
                .map(e -> serializer.deserialize((byte[]) e, Session.class))
                .switchIfEmpty(Mono.empty());
    }

    @Override
    public Mono<Boolean> clear(String clientId) {
        return redisTemplate.delete(messageIdPrefix + clientId)
                .then(redisTemplate.opsForHash().remove(clusterSessionHashKey, clientId))
                .switchIfEmpty(Mono.just(-1L))
                .map(e -> e > 0);
    }

    @Override
    public Mono<Boolean> hasKey(String clientId) {
        return redisTemplate.opsForHash().hasKey(clusterSessionHashKey, clientId);
    }

    @Override
    public Mono<Integer> nextMessageId(String clientId) {
        return redisTemplate.opsForValue().increment(messageIdPrefix + clientId)
                .flatMap(e -> {
                    if (Objects.equals(e, 0L)) {
                        return redisTemplate.opsForValue().increment(messageIdPrefix + clientId);
                    }
                    return Mono.just(e);
                })
                .map(Math::toIntExact);
    }
}
