package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IPublishMessageService;
import com.jun.mqttx.utils.Serializer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * publish message store by redis
 *
 * @author Jun
 * @since 1.0.4
 */
@Service
public class DefaultPublishMessageServiceImpl implements IPublishMessageService {

    private final RedisTemplate<String, byte[]> redisTemplate;
    private final Serializer serializer;
    private final String pubMsgSetPrefix;

    public DefaultPublishMessageServiceImpl(RedisTemplate<String, byte[]> redisTemplate,
                                            Serializer serializer,
                                            MqttxConfig mqttxConfig) {
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;

        this.pubMsgSetPrefix = mqttxConfig.getRedis().getPubMsgSetPrefix();
        Assert.hasText(pubMsgSetPrefix, "pubMsgSetPrefix can't be null");
    }

    @Override
    public void save(String clientId, PubMsg pubMsg) {
        redisTemplate.opsForHash().put(pubMsgSetPrefix + clientId,
                String.valueOf(pubMsg.getMessageId()), serializer.serialize(pubMsg));
    }

    @Override
    public void clear(String clientId) {
        redisTemplate.delete(pubMsgSetPrefix + clientId);
    }

    @Override
    public void remove(String clientId, int messageId) {
        redisTemplate.opsForHash().delete(
                key(clientId),
                String.valueOf(messageId)
        );
    }

    @Override
    public List<PubMsg> search(String clientId) {
        List<Object> values = redisTemplate.opsForHash().values(key(clientId));
        if (CollectionUtils.isEmpty(values)) {
            // noinspection unchecked
            return Collections.EMPTY_LIST;
        }

        return values.stream()
                .map(o -> serializer.deserialize((byte[]) o, PubMsg.class))
                .collect(Collectors.toList());
    }

    private String key(String client) {
        return pubMsgSetPrefix + client;
    }
}
