package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.service.IPubRelMessageService;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate<String, byte[]> redisTemplate;
    private final String pubRelMsgSetPrefix;

    public DefaultPubRelMessageServiceImpl(RedisTemplate<String, byte[]> redisTemplate, MqttxConfig mqttxConfig) {
        this.redisTemplate = redisTemplate;

        this.pubRelMsgSetPrefix = mqttxConfig.getRedis().getPubRelMsgSetPrefix();
        Assert.notNull(pubRelMsgSetPrefix, "pubRelMsgSetPrefix can't be null");
    }

    @Override
    public void saveOut(String clientId, int messageId) {
        redisTemplate.opsForSet()
                .add(outKey(clientId), int2bytes(messageId));
    }

    @Override
    public void saveIn(String clientId, int messageId) {
        redisTemplate.opsForSet()
                .add(inKey(clientId), int2bytes(messageId));
    }

    @Override
    public boolean isInMsgDup(String clientId, int messageId) {
        Boolean member = redisTemplate.opsForSet()
                .isMember(inKey(clientId), int2bytes(messageId));
        return Boolean.TRUE.equals(member);
    }

    @Override
    public void removeIn(String clientId, int messageId) {
        redisTemplate.opsForSet()
                .remove(inKey(clientId), (Object) int2bytes(messageId));
    }

    @Override
    public void removeOut(String clientId, int messageId) {
        redisTemplate.opsForSet()
                .remove(outKey(clientId), (Object) int2bytes(messageId));
    }

    @Override
    public List<Integer> searchOut(String clientId) {
        Set<byte[]> members = redisTemplate.opsForSet().members(outKey(clientId));
        if (CollectionUtils.isEmpty(members)) {
            //noinspection unchecked
            return Collections.EMPTY_LIST;
        }

        return members.stream()
                .map(this::bytes2int)
                .collect(Collectors.toList());
    }

    @Override
    public void clear(String clientId) {
        redisTemplate.delete(inKey(clientId));
        redisTemplate.delete(outKey(clientId));
    }

    private String inKey(String clientId) {
        return pubRelMsgSetPrefix + clientId + OUT;
    }

    private String outKey(String clientId) {
        return pubRelMsgSetPrefix + clientId + IN;
    }

    private byte[] int2bytes(int msg) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((msg >> (8 * (3 - i))) & 0xff);
        }
        return bytes;
    }

    private int bytes2int(byte[] msg) {
        if (msg == null || msg.length != 4) {
            throw new IllegalArgumentException(String.format("无法将数组 %s 转为 int", Arrays.toString(msg)));
        }

        int result = 0;
        for (int i = 0; i < 4; i++) {
            result += (msg[i] & 0xff) << (8 * (3 - i));
        }
        return result;
    }
}
