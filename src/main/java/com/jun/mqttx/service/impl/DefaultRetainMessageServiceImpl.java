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
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IRetainMessageService;
import com.jun.mqttx.utils.Serializer;
import com.jun.mqttx.utils.TopicUtils;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * 存储通过 redis 实现
 *
 * @author Jun
 * @since 1.0.4
 */
@Service
public class DefaultRetainMessageServiceImpl implements IRetainMessageService {

    //@formatter:off
    /** redis retain message prefix */
    private final String retainMessageHashKey;
    private final ReactiveRedisTemplate<String, byte[]> redisTemplate;
    private final Serializer serializer;
    //@formatter:on

    public DefaultRetainMessageServiceImpl(ReactiveRedisTemplate<String, byte[]> redisTemplate, Serializer serializer,
                                           MqttxConfig mqttxConfig) {
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
        this.retainMessageHashKey = mqttxConfig.getRedis().getRetainMessagePrefix();
        Assert.hasText(retainMessageHashKey, "retainMessagePrefix can't be null");
    }

    @Override
    public Flux<PubMsg> searchListByTopicFilter(String newSubTopic) {
        return redisTemplate.opsForHash()
                .keys(retainMessageHashKey)
                .filter(t -> TopicUtils.match((String) t, newSubTopic))
                .collectList()
                .flatMap(t -> {
                    if (!ObjectUtils.isEmpty(t)) {
                        return redisTemplate.opsForHash().multiGet(retainMessageHashKey, t);
                    }else {
                        return Mono.empty();
                    }
                })
                .flatMapIterable(Function.identity())
                .map(o -> serializer.deserialize((byte[]) o, PubMsg.class));
    }

    @Override
    public Mono<Void> save(String topic, PubMsg pubMsg) {
        return redisTemplate.opsForHash().put(retainMessageHashKey, topic, serializer.serialize(pubMsg)).then();
    }

    @Override
    public Mono<Void> remove(String topic) {
        return redisTemplate.opsForHash().remove(retainMessageHashKey, topic).then();
    }

    @Override
    public Mono<PubMsg> get(String topic) {
        return redisTemplate.opsForHash().get(retainMessageHashKey, topic)
                .map(e -> serializer.deserialize((byte[]) e, PubMsg.class));
    }
}
