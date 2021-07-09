/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    public AbstractMqttTopicSecureHandler(boolean enableCluster) {
        super(enableCluster);
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
