package com.jun.mqttx.broker.handler;

import com.jun.mqttx.entity.Session;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

/**
 * 改抽象类提供 {@link Session} 相关方法
 *
 * @author Jun
 * @date 2020-03-07 22:20
 */
public abstract class AbstractMqttSessionHandler implements MqttMessageHandler {

    /**
     * 生成消息ID
     *
     * @param ctx {@link ChannelHandlerContext}
     * @return 消息ID
     */
    int nextMessageId(ChannelHandlerContext ctx) {
        Session session = getSession(ctx);
        return session.increaseAndGetMessageId();
    }

    /**
     * 返回客户id
     *
     * @param ctx {@link ChannelHandlerContext}
     * @return clientId
     */
    String clientId(ChannelHandlerContext ctx) {
        Session session = getSession(ctx);
        return session.getClientId();
    }

    /**
     * 获取当前会话的 clearSession
     *
     * @param ctx {@link ChannelHandlerContext}
     * @return true if clearSession = 1
     */
    boolean clearSession(ChannelHandlerContext ctx) {
        Session session = getSession(ctx);
        return session.getClearSession();
    }

    /**
     * 存储当前会话状态
     *
     * @param ctx     {@link ChannelHandlerContext}
     * @param session mqtt会话
     */
    void saveSessionWithChannel(ChannelHandlerContext ctx, Session session) {
        Channel channel = ctx.channel();
        AttributeKey<Object> attr = AttributeKey.valueOf("session");
        channel.attr(attr).set(session);
    }

    /**
     * 获取客户会话
     *
     * @param ctx {@link ChannelHandlerContext}
     * @return {@link Session}
     */
    private Session getSession(ChannelHandlerContext ctx) {
        return (Session) ctx.channel().attr(AttributeKey.valueOf("session")).get();
    }
}
