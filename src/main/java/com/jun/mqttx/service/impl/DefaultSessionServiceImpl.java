/*
 * Copyright 2020-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.Session;
import com.jun.mqttx.service.ISessionService;
import com.jun.mqttx.utils.MessageIdUtils;
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
                .map(e -> serializer.deserialize((byte[]) e, Session.class));
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
                    if ((e & 0xffff) == 0) {
                        return redisTemplate.opsForValue().increment(messageIdPrefix + clientId);
                    }
                    return Mono.just(e);
                })
                .map(t -> MessageIdUtils.trimMessageId(Math.toIntExact(t)));
    }
}
