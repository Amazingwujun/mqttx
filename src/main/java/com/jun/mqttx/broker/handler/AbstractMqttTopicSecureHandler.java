package com.jun.mqttx.broker.handler;

import com.jun.mqttx.utils.TopicUtils;
import io.netty.channel.ChannelHandlerContext;

/**
 * 负责 topic 权限认证，运行规则：
 * <ol>
 *     <li>获取用户被允许订阅&发布的 topic 列表</li>
 *     <li>返回当前订阅&发布的 topic 是否被授权</li>
 * </ol>
 *
 * @author Jun
 * @since 1.0.4
 */
public abstract class AbstractMqttTopicSecureHandler extends AbstractMqttSessionHandler {

    public AbstractMqttTopicSecureHandler(boolean enableTestMode, boolean enableCluster) {
        super(enableTestMode, enableCluster);
    }

    /**
     * client 是否被允许订阅 topic
     *
     * @param ctx   {@link ChannelHandlerContext}
     * @param topic 订阅 topic
     * @return true 如果被授权
     */
    boolean hasAuthToSubTopic(ChannelHandlerContext ctx, String topic) {
        return TopicUtils.hasAuthToSubTopic(ctx, topic);
    }

    /**
     * client 是否允许发布消息到指定 topic
     *
     * @param ctx   {@link ChannelHandlerContext}
     * @param topic 订阅 topic
     * @return true if client authorised
     */
    boolean hasAuthToPubTopic(ChannelHandlerContext ctx, String topic) {
        return TopicUtils.hasAuthToPubTopic(ctx, topic);
    }
}