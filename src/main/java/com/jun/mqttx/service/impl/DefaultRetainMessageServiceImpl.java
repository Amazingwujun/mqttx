package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IRetainMessageService;
import com.jun.mqttx.utils.Serializer;
import com.jun.mqttx.utils.TopicUtils;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
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
        Assert.notNull(redisTemplate, "stringRedisTemplate can't be null");

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
                .flatMap(t -> redisTemplate.opsForHash().multiGet(retainMessageHashKey, t))
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
