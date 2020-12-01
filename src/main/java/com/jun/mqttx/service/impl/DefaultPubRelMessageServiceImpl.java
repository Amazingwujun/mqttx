package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.service.IPubRelMessageService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于 redis 的实现
 *
 * @author Jun
 * @since 1.0.4
 */
@Component
public class DefaultPubRelMessageServiceImpl implements IPubRelMessageService {

    private static final String IN = "_IN";
    private static final String OUT = "_OUT";
    private final StringRedisTemplate stringRedisTemplate;
    private final String pubRelMsgSetPrefix;
    private final boolean enableTestMode;
    private Map<String, Set<Integer>> clientMsgStore;

    public DefaultPubRelMessageServiceImpl(StringRedisTemplate stringRedisTemplate, MqttxConfig mqttxConfig) {
        this.stringRedisTemplate = stringRedisTemplate;

        this.pubRelMsgSetPrefix = mqttxConfig.getRedis().getPubRelMsgSetPrefix();
        this.enableTestMode = mqttxConfig.getEnableTestMode();
        if (enableTestMode) {
            clientMsgStore = new ConcurrentHashMap<>();
        }
        Assert.notNull(pubRelMsgSetPrefix, "pubRelMsgSetPrefix can't be null");
    }

    @Override
    public void saveOut(String clientId, int messageId) {
        if (enableTestMode) {
            clientMsgStore.computeIfAbsent(outKey(clientId), s -> ConcurrentHashMap.newKeySet()).add(messageId);
            return;
        }

        stringRedisTemplate.opsForSet()
                .add(outKey(clientId), String.valueOf(messageId));
    }

    @Override
    public void saveIn(String clientId, int messageId) {
        if (enableTestMode) {
            clientMsgStore.computeIfAbsent(inKey(clientId), s -> ConcurrentHashMap.newKeySet()).add(messageId);
            return;
        }

        stringRedisTemplate.opsForSet()
                .add(inKey(clientId), String.valueOf(messageId));
    }

    @Override
    public boolean isInMsgDup(String clientId, int messageId) {
        if (enableTestMode) {
            return clientMsgStore
                    .computeIfAbsent(inKey(clientId), s -> ConcurrentHashMap.newKeySet())
                    .contains(messageId);
        }

        Boolean member = stringRedisTemplate.opsForSet()
                .isMember(inKey(clientId), String.valueOf(messageId));
        return Boolean.TRUE.equals(member);
    }

    @Override
    public void removeIn(String clientId, int messageId) {
        if (enableTestMode) {
            clientMsgStore.computeIfAbsent(inKey(clientId), s -> ConcurrentHashMap.newKeySet()).remove(messageId);
            return;
        }

        stringRedisTemplate.opsForSet()
                .remove(inKey(clientId), String.valueOf(messageId));
    }

    @Override
    public void removeOut(String clientId, int messageId) {
        if (enableTestMode) {
            clientMsgStore.computeIfAbsent(outKey(clientId), s -> ConcurrentHashMap.newKeySet()).remove(messageId);
            return;
        }

        stringRedisTemplate.opsForSet()
                .remove(outKey(clientId), String.valueOf(messageId));
    }

    @Override
    public List<Integer> searchOut(String clientId) {
        if (enableTestMode) {
            return new ArrayList<>(
                    clientMsgStore.computeIfAbsent(outKey(clientId), s -> ConcurrentHashMap.newKeySet())
            );
        }

        Set<String> members = stringRedisTemplate.opsForSet().members(outKey(clientId));
        if (CollectionUtils.isEmpty(members)) {
            //noinspection unchecked
            return Collections.EMPTY_LIST;
        }

        return members.stream()
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    @Override
    public void clear(String clientId) {
        if (enableTestMode) {
            clientMsgStore.remove(outKey(clientId));
            clientMsgStore.remove(inKey(clientId));
            return;
        }

        stringRedisTemplate.delete(inKey(clientId));
        stringRedisTemplate.delete(outKey(clientId));
    }

    private String inKey(String clientId) {
        return pubRelMsgSetPrefix + clientId + OUT;
    }

    private String outKey(String clientId) {
        return pubRelMsgSetPrefix + clientId + IN;
    }
}