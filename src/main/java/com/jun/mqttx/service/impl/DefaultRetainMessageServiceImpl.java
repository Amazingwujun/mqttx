package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IRetainMessageService;
import com.jun.mqttx.utils.Serializer;
import com.jun.mqttx.utils.TopicUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    private final RedisTemplate<String, byte[]> redisTemplate;
    private final Serializer serializer;
    //@formatter:on

    public DefaultRetainMessageServiceImpl(RedisTemplate<String, byte[]> redisTemplate, Serializer serializer,
                                           MqttxConfig mqttxConfig) {
        Assert.notNull(redisTemplate, "stringRedisTemplate can't be null");

        this.redisTemplate = redisTemplate;
        this.serializer = serializer;
        this.retainMessageHashKey = mqttxConfig.getRedis().getRetainMessagePrefix();

        Assert.hasText(retainMessageHashKey, "retainMessagePrefix can't be null");
    }

    @Override
    public List<PubMsg> searchListByTopicFilter(String newSubTopic) {
        List<Object> collect = redisTemplate.opsForHash()
                .keys(retainMessageHashKey)
                .stream()
                .filter(o -> TopicUtils.match((String) o, newSubTopic)).collect(Collectors.toList());
        return redisTemplate.opsForHash()
                .multiGet(retainMessageHashKey, collect)
                .stream()
                .map(o -> serializer.deserialize((byte[]) o, PubMsg.class))
                .collect(Collectors.toList());
    }

    @Override
    public void save(String topic, PubMsg pubMsg) {
        redisTemplate.opsForHash().put(retainMessageHashKey, topic, serializer.serialize(pubMsg));
    }

    @Override
    public void remove(String topic) {
        redisTemplate.opsForHash().delete(retainMessageHashKey, topic);
    }

    @Override
    public PubMsg get(String topic) {
        byte[] pubMsg = (byte[]) redisTemplate.opsForHash().get(retainMessageHashKey, topic);
        return serializer.deserialize(pubMsg, PubMsg.class);
    }
}
