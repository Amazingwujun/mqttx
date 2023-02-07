package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.service.IPubRelMessageService;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;

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
    private final ReactiveRedisTemplate<String, byte[]> redisTemplate;
    private final String pubRelMsgSetPrefix;

    public DefaultPubRelMessageServiceImpl(ReactiveRedisTemplate<String, byte[]> redisTemplate, MqttxConfig mqttxConfig) {
        this.redisTemplate = redisTemplate;

        this.pubRelMsgSetPrefix = mqttxConfig.getRedis().getPubRelMsgSetPrefix();
        Assert.notNull(pubRelMsgSetPrefix, "pubRelMsgSetPrefix can't be null");
    }

    @Override
    public Mono<Void> saveOut(String clientId, int messageId) {
        return redisTemplate.opsForSet()
                .add(outKey(clientId), int2bytes(messageId))
                .then();
    }

    @Override
    public Mono<Void> saveIn(String clientId, int messageId) {
        return redisTemplate.opsForSet()
                .add(inKey(clientId), int2bytes(messageId))
                .then();
    }

    @Override
    public Mono<Boolean> isInMsgDup(String clientId, int messageId) {
        return redisTemplate.opsForSet()
                .isMember(inKey(clientId), int2bytes(messageId))
                .switchIfEmpty(Mono.just(false));
    }

    @Override
    public Mono<Void> removeIn(String clientId, int messageId) {
        return redisTemplate.opsForSet()
                .remove(inKey(clientId), (Object) int2bytes(messageId))
                .then();
    }

    @Override
    public Mono<Void> removeOut(String clientId, int messageId) {
        return redisTemplate.opsForSet()
                .remove(outKey(clientId), (Object) int2bytes(messageId))
                .then();
    }

    @Override
    public Flux<Integer> searchOut(String clientId) {
        return redisTemplate.opsForSet().members(outKey(clientId))
                .map(this::bytes2int);
    }

    @Override
    public Mono<Void> clear(String clientId) {
        return Mono.when(redisTemplate.delete(inKey(clientId)), redisTemplate.delete(outKey(clientId)));
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
