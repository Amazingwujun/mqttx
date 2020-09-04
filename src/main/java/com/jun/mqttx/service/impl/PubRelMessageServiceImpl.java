package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.service.IPubRelMessageService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 redis 的实现
 *
 * @author Jun
 * @date 2020-03-17 09:36
 */
@Component
public class PubRelMessageServiceImpl implements IPubRelMessageService {

    private StringRedisTemplate stringRedisTemplate;

    private String pubRelMsgSetPrefix;

    public PubRelMessageServiceImpl(StringRedisTemplate stringRedisTemplate, MqttxConfig mqttxConfig) {
        this.stringRedisTemplate = stringRedisTemplate;

        this.pubRelMsgSetPrefix = mqttxConfig.getRedis().getPubRelMsgSetPrefix();
        Assert.notNull(pubRelMsgSetPrefix, "pubRelMsgSetPrefix can't be null");
    }

    @Override
    public void save(String clientId, int messageId) {
        stringRedisTemplate.opsForSet()
                .add(pubRelMsgSetPrefix + clientId, String.valueOf(messageId));
    }

    @Override
    public boolean isDupMsg(String clientId, int messageId) {
        Boolean member = stringRedisTemplate.opsForSet()
                .isMember(key(clientId), String.valueOf(messageId));
        return Boolean.TRUE.equals(member);
    }

    @Override
    public void remove(String clientId, int messageId) {
        stringRedisTemplate.opsForSet()
                .remove(pubRelMsgSetPrefix + clientId, String.valueOf(messageId));
    }

    @Override
    public List<Integer> search(String clientId) {
        Set<String> members = stringRedisTemplate.opsForSet().members(key(clientId));
        if (CollectionUtils.isEmpty(members)) {
            return Collections.EMPTY_LIST;
        }

        return members.stream()
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    @Override
    public void clear(String clientId) {
        stringRedisTemplate.delete(key(clientId));
    }

    private String key(String clientId) {
        return pubRelMsgSetPrefix + clientId;
    }
}
