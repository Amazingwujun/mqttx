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

package com.jun.mqttx.config;

import com.jun.mqttx.consumer.DefaultInternalMessageSubscriber;
import com.jun.mqttx.consumer.KafkaInternalMessageSubscriber;
import com.jun.mqttx.consumer.Watcher;
import com.jun.mqttx.service.IInternalMessagePublishService;
import com.jun.mqttx.service.impl.DefaultInternalMessagePublishServiceImpl;
import com.jun.mqttx.service.impl.KafkaInternalMessagePublishServiceImpl;
import com.jun.mqttx.utils.Serializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static com.jun.mqttx.constants.InternalMessageEnum.*;

/**
 * 集群配置, 两种实现:
 * <ul>
 *     <li>redis</li>
 *     <li>kafka</li>
 * </ul>
 * 默认采用 redis 实现
 *
 * @author Jun
 * @since 1.0.4
 */
@Configuration
@ConditionalOnExpression("${mqttx.cluster.enable:false}")
public class ClusterConfig {

    public static final String REDIS = "redis";
    public static final String KAFKA = "kafka";

    @Bean
    @ConditionalOnProperty(name = "mqttx.cluster.type", havingValue = REDIS, matchIfMissing = true)
    public DefaultInternalMessageSubscriber defaultInternalMessageSubscriber(List<Watcher> watchers,
                                                                             Serializer serializer,
                                                                             MqttxConfig mqttxConfig) {
        return new DefaultInternalMessageSubscriber(watchers, serializer, mqttxConfig);
    }

    @Bean
    @ConditionalOnProperty(name = "mqttx.cluster.type", havingValue = KAFKA)
    public KafkaInternalMessageSubscriber kafkaInternalMessageSubscriber(List<Watcher> watchers,
                                                                         Serializer serializer, MqttxConfig mqttxConfig) {
        return new KafkaInternalMessageSubscriber(watchers, serializer, mqttxConfig);
    }

    @Bean
    @ConditionalOnProperty(name = "mqttx.cluster.type", havingValue = REDIS, matchIfMissing = true)
    public IInternalMessagePublishService defaultInternalMessagePublishServiceImpl(ReactiveRedisTemplate<String, byte[]> redisTemplate,
                                                                                   Serializer serializer) {
        return new DefaultInternalMessagePublishServiceImpl(redisTemplate, serializer);
    }

    @Bean
    @ConditionalOnProperty(name = "mqttx.cluster.type", havingValue = KAFKA)
    public IInternalMessagePublishService kafkaInternalMessagePublishServiceImpl(KafkaTemplate<String, byte[]> kafkaTemplate, Serializer serializer) {
        return new KafkaInternalMessagePublishServiceImpl(kafkaTemplate, serializer);
    }

    /**
     * 消息监听者容器
     *
     * @param redisConnectionFactory {@link RedisConnectionFactory}
     * @param subscriber             {@link DefaultInternalMessageSubscriber}
     */
    @Bean
    @ConditionalOnProperty(name = "mqttx.cluster.type", havingValue = REDIS, matchIfMissing = true)
    public ReactiveRedisMessageListenerContainer redisMessageListenerContainer(ReactiveRedisConnectionFactory redisConnectionFactory,
                                                                               DefaultInternalMessageSubscriber subscriber) {
        var redisMessageListenerContainer = new ReactiveRedisMessageListenerContainer(redisConnectionFactory);
        var channelTopics = List.of(
                new ChannelTopic(PUB.getChannel()),
                new ChannelTopic(PUB_ACK.getChannel()),
                new ChannelTopic(PUB_REC.getChannel()),
                new ChannelTopic(PUB_COM.getChannel()),
                new ChannelTopic(PUB_REL.getChannel()),
                new ChannelTopic(DISCONNECT.getChannel()),
                new ChannelTopic(ALTER_USER_AUTHORIZED_TOPICS.getChannel()),
                new ChannelTopic(SUB_UNSUB.getChannel())
        );
        redisMessageListenerContainer.receive(
                channelTopics,
                RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()),
                RedisSerializationContext.SerializationPair.byteArray()
        ).subscribe(t -> subscriber.handleMessage(t.getMessage(), t.getChannel()));

        return redisMessageListenerContainer;
    }
}
