package com.jun.mqttx.service.impl;

import com.jun.mqttx.broker.handler.PublishHandler;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.entity.SourcePayload;
import com.jun.mqttx.service.IPublishMessageService;
import com.jun.mqttx.utils.Serializer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * publish message store by redis
 *
 * @author Jun
 * @since 1.0.4
 */
@Service
public class DefaultPublishMessageServiceImpl implements IPublishMessageService {
    //@formatter:off

    private final RedisTemplate<String, byte[]> redisTemplate;
    private final Serializer serializer;
    private final String pubMsgSetPrefix, msgPayLoadClientsSetKey, msgPayloadKey;
    private final boolean enableTestMode;
    private Map<String, Map<Integer, PubMsg>> pubMsgStore;
    /** 发布消息体缓存 */
    private final Map<String,byte[]> payloadCache = new ConcurrentHashMap<>();
    //@formatter:on

    public DefaultPublishMessageServiceImpl(RedisTemplate<String, byte[]> redisTemplate,
                                            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") Serializer serializer,
                                            MqttxConfig mqttxConfig) {
        this.redisTemplate = redisTemplate;
        this.serializer = serializer;

        MqttxConfig.Redis redis = mqttxConfig.getRedis();
        this.pubMsgSetPrefix = redis.getPubMsgSetPrefix();
        this.msgPayLoadClientsSetKey = redis.getMsgPayLoadClientsSetKey();
        this.msgPayloadKey = redis.getMsgPayLoadKey();
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

        redisTemplate.opsForHash().put(pubMsgSetPrefix + clientId,
                String.valueOf(pubMsg.getMessageId()), serializer.serialize(pubMsg));
    }

    @Override
    public void savePayloadAndClientBinding(List<String> clientSubs, SourcePayload sourcePayload) {
        if (enableTestMode) {
            return;
        }

        byte[][] bytes = clientSubs.stream().map(serializer::serialize).toArray(byte[][]::new);
        redisTemplate.opsForSet().add(msgPayLoadClientsSetKey + sourcePayload.getId(), bytes);
        redisTemplate.opsForHash().put(msgPayloadKey, sourcePayload.getId(), sourcePayload.getPayload());
    }

    @Override
    public void clear(String clientId) {
        if (enableTestMode) {
            pubMsgStore.remove(clientId);
            return;
        }

        redisTemplate.delete(key(clientId));
    }

    @Override
    public void remove(String clientId, int messageId) {
        if (enableTestMode) {
            pubMsgStore.computeIfAbsent(clientId, s -> new ConcurrentHashMap<>()).remove(messageId);
            return;
        }

        // todo 使用 redis script 实现
        // 1. 删除 pubMsg
        // 2. 删除 client 和 payloadId 的关联关系
        // 3. 如果 client 与 payloadId 所在 redis set 已经为空，则删除此 set, 否则返回
        redisTemplate.opsForHash().delete(
                key(clientId),
                String.valueOf(messageId)
        );
        redisTemplate.opsForSet().remove(msgPayLoadClientsSetKey, clientId + messageId);
        Long size = redisTemplate.opsForSet().size(msgPayLoadClientsSetKey);
        if (Objects.equals(0L,size)) {
            redisTemplate.opsForHash().delete(msgPayloadKey, clientId + messageId);
        }
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

        List<Object> values = redisTemplate.opsForHash().values(key(clientId));
        if (CollectionUtils.isEmpty(values)) {
            // noinspection unchecked
            return Collections.EMPTY_LIST;
        }
        List<PubMsg> pubMsgs = values.stream()
                .map(o -> serializer.deserialize((byte[]) o, PubMsg.class))
                .collect(Collectors.toList());
        List<String> payloadIds = pubMsgs.stream()
                .map(PubMsg::getPayloadId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<Object> payloads = redisTemplate.opsForHash().multiGet(msgPayloadKey, payloadIds);
        for (Object payloadId : payloadIds) {

        }



        return values.stream()
                .map(o -> serializer.deserialize((byte[]) o, PubMsg.class))
                .collect(Collectors.toList());
    }

    private String key(String client) {
        return pubMsgSetPrefix + client;
    }
}