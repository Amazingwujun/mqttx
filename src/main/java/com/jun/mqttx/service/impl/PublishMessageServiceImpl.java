package com.jun.mqttx.service.impl;

import com.alibaba.fastjson.JSON;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IPublishMessageService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * publish message store by redis
 *
 * @author Jun
 * @since 1.0.4
 */
@Service
public class PublishMessageServiceImpl implements IPublishMessageService {

    private StringRedisTemplate stringRedisTemplate;

    private String pubMsgSetPrefix;

    private Boolean enableTestMode;
    private Map<String, Map<Integer, PubMsg>> pubMsgStore;

    public PublishMessageServiceImpl(StringRedisTemplate stringRedisTemplate, MqttxConfig mqttxConfig) {
        this.stringRedisTemplate = stringRedisTemplate;

        this.pubMsgSetPrefix = mqttxConfig.getRedis().getPubMsgSetPrefix();
        this.enableTestMode = mqttxConfig.getEnableTestMode();
        if (enableTestMode) {
            pubMsgStore = new ConcurrentHashMap<>();
        }
        Assert.hasText(pubMsgSetPrefix, "pubMsgSetPrefix can't be null");
    }

    @Override
    public void save(String clientId, PubMsg pubMsg) {
        if (enableTestMode) {
            pubMsgStore
                    .computeIfAbsent(clientId, s -> new ConcurrentHashMap<>())
                    .put(pubMsg.getMessageId(), pubMsg);
            return;
        }

        stringRedisTemplate.opsForHash().put(pubMsgSetPrefix + clientId,
                String.valueOf(pubMsg.getMessageId()), JSON.toJSONString(pubMsg));
    }

    @Override
    public void clear(String clientId) {
        if (enableTestMode) {
            pubMsgStore.remove(clientId);
            return;
        }

        stringRedisTemplate.delete(pubMsgSetPrefix + clientId);
    }

    @Override
    public void remove(String clientId, int messageId) {
        if (enableTestMode) {
            pubMsgStore.computeIfAbsent(clientId, s -> new ConcurrentHashMap<>()).remove(messageId);
            return;
        }

        stringRedisTemplate.opsForHash().delete(
                key(clientId),
                String.valueOf(messageId)
        );
    }

    @Override
    public List<PubMsg> search(String clientId) {
        if (enableTestMode) {
            List<PubMsg> values = new ArrayList<>();
            pubMsgStore.computeIfPresent(clientId, (s, pubMsgMap) -> {
                values.addAll(pubMsgMap.values());
                return pubMsgMap;
            });
            return values;
        }

        List<Object> values = stringRedisTemplate.opsForHash().values(key(clientId));
        if (CollectionUtils.isEmpty(values)) {
            //noinspection unchecked
            return Collections.EMPTY_LIST;
        }

        return values.stream()
                .map(o -> JSON.parseObject((String) o, PubMsg.class))
                .collect(Collectors.toList());
    }

    private String key(String client) {
        return pubMsgSetPrefix + client;
    }
}