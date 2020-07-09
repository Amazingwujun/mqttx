package com.jun.mqttx.broker.handler;

import com.jun.mqttx.utils.TopicUtils;
import io.netty.channel.ChannelHandlerContext;

/**
 * 负责 topic 权限认证，运行规则：
 * <ol>
 *     <li>获取用户被允许订阅的 authorizedTopic</li>
 *     <li>校验当前订阅的 topic 与 authorizedTopic 是否匹匹配</li>
 * </ol>
 * <p>
 * 举例：a 用户 authorizedTopic = {"a/b","device/#"},则 a 可以订阅topic a/b,device/a/bc,device/nani等等
 *
 * @author Jun
 * @date 2020-06-09 15:34
 */
public abstract class AbstractMqttTopicSecureHandler extends AbstractMqttSessionHandler {


    /**
     * client 是否被允许订阅 topic
     *
     * @param ctx   {@link ChannelHandlerContext}
     * @param topic 订阅 topic
     * @return true 如果被授权
     */
    protected boolean hasAuthToSubTopic(ChannelHandlerContext ctx, String topic) {
        return TopicUtils.hasAuthToSubTopic(ctx, topic);
    }

    protected boolean hasAuthToPubTopic(ChannelHandlerContext ctx, String topic) {
        return TopicUtils.hasAuthToPubTopic(ctx, topic);
    }
}
