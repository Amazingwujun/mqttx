package com.jun.mqttx.service.impl;

import com.alibaba.fastjson.JSON;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IRetainMessageService;
import com.jun.mqttx.utils.TopicUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
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
public class RetainMessageServiceImpl implements IRetainMessageService {

    /**
     * redis retain message prefix
     */
    private final String retainMessageHashKey;
    private StringRedisTemplate stringRedisTemplate;
    private Boolean enableTestMode;
    private Map<String, PubMsg> pubMsgStore;

    public RetainMessageServiceImpl(StringRedisTemplate stringRedisTemplate, MqttxConfig mqttxConfig) {
        Assert.notNull(stringRedisTemplate, "stringRedisTemplate can't be null");

        this.stringRedisTemplate = stringRedisTemplate;
        this.retainMessageHashKey = mqttxConfig.getRedis().getRetainMessagePrefix();
        this.enableTestMode = mqttxConfig.getEnableTestMode();
        if (enableTestMode) {
            pubMsgStore = new ConcurrentHashMap<>();
        }

        Assert.hasText(retainMessageHashKey, "retainMessagePrefix can't be null");
    }

    @Override
    public List<PubMsg> searchListBySubTopic(String newSubTopic) {
        if (enableTestMode) {
            return pubMsgStore.entrySet().stream()
                    .filter(stringPubMsgEntry -> TopicUtils.match(stringPubMsgEntry.getKey(), newSubTopic))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        }

        List<Object> collect = stringRedisTemplate.opsForHash()
                .keys(retainMessageHashKey)
                .stream()
                .filter(o -> TopicUtils.match((String) o, newSubTopic)).collect(Collectors.toList());
        return stringRedisTemplate.opsForHash()
                .multiGet(retainMessageHashKey, collect)
                .stream()
                .map(o -> JSON.parseObject((String) o, PubMsg.class))
                .collect(Collectors.toList());
    }

    @Override
    public void save(String topic, PubMsg pubMsg) {
        if (enableTestMode) {
            pubMsgStore.put(topic, pubMsg);
            return;
        }

        stringRedisTemplate.opsForHash().put(retainMessageHashKey, topic, JSON.toJSONString(pubMsg));
    }

    @Override
    public void remove(String topic) {
        if (enableTestMode) {
            pubMsgStore.remove(topic);
            return;
        }

        stringRedisTemplate.opsForHash().delete(retainMessageHashKey, topic);
    }

    @Override
    public PubMsg get(String topic) {
        if (enableTestMode) {
            return pubMsgStore.get(topic);
        }

        String pubMsg = (String) stringRedisTemplate.opsForHash().get(retainMessageHashKey, topic);
        return JSON.parseObject(pubMsg, PubMsg.class);
    }
}