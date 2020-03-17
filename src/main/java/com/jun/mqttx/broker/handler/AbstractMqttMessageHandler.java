package com.jun.mqttx.broker.handler;

import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.entity.Session;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.Assert;

/**
 * 部分通用方法
 *
 * @author Jun
 * @date 2020-03-07 22:20
 */
public abstract class AbstractMqttMessageHandler implements MqttMessageHandler {

    private StringRedisTemplate stringRedisTemplate;

    private String messageIdPrefix;

    public AbstractMqttMessageHandler(StringRedisTemplate stringRedisTemplate, BizConfig bizConfig) {
        Assert.notNull(stringRedisTemplate, "stringRedisTemplate can't be null");

        this.stringRedisTemplate = stringRedisTemplate;
        this.messageIdPrefix = bizConfig.getMessageIdPrefix();

        Assert.hasText(messageIdPrefix, "messageIdPrefix can't be null");
    }

    /**
     * 生成消息ID
     *
     * @param clientId 客户端identifier
     * @return 消息ID
     */
    int nextMessageId(String clientId) {
        return stringRedisTemplate.opsForValue()
                .increment(messageIdPrefix + clientId).intValue();
    }

    /**
     * 返回客户id
     *
     * @param ctx {@link ChannelHandlerContext}
     * @return clientId
     */
    String clientId(ChannelHandlerContext ctx) {
        Session session = (Session) ctx.channel().attr(AttributeKey.valueOf("session")).get();
        return session.getClientId();
    }

    /**
     * 获取当前会话的 clearSession
     *
     * @param ctx {@link ChannelHandlerContext}
     * @return true if clearSession = 1
     */
    boolean clearSession(ChannelHandlerContext ctx) {
        Session session = (Session) ctx.channel().attr(AttributeKey.valueOf("session")).get();
        return session.getClearSession();
    }

    /**
     * 存储当前会话状态
     *
     * @param ctx     {@link ChannelHandlerContext}
     * @param session mqtt会话
     */
    void saveSession(ChannelHandlerContext ctx, Session session) {
        Channel channel = ctx.channel();
        AttributeKey<Object> attr = AttributeKey.valueOf("session");
        channel.attr(attr).set(session);
    }
}
