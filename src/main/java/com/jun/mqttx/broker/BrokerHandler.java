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

package com.jun.mqttx.broker;

import com.alibaba.fastjson.TypeReference;
import com.jun.mqttx.broker.handler.AbstractMqttSessionHandler;
import com.jun.mqttx.broker.handler.ConnectHandler;
import com.jun.mqttx.broker.handler.MessageDelegatingHandler;
import com.jun.mqttx.broker.handler.PublishHandler;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.constants.InternalMessageEnum;
import com.jun.mqttx.consumer.Watcher;
import com.jun.mqttx.entity.Authentication;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.entity.Session;
import com.jun.mqttx.exception.AuthenticationException;
import com.jun.mqttx.exception.AuthorizationException;
import com.jun.mqttx.service.ISessionService;
import com.jun.mqttx.service.ISubscriptionService;
import com.jun.mqttx.utils.JsonSerializer;
import com.jun.mqttx.utils.Serializer;
import com.jun.mqttx.utils.TopicUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * broker handler
 *
 * @author Jun
 * @since 1.0.4
 */
@Slf4j
@ChannelHandler.Sharable
@Component
public class BrokerHandler extends SimpleChannelInboundHandler<MqttMessage> implements Watcher {
    //@formatter:off
    /** channel 群组 */
    public static final ChannelGroup CHANNELS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    /** 历史最大连接数量 */
    public static final AtomicInteger MAX_ACTIVE_SIZE = new AtomicInteger(0);
    /** broker 启动时间 */
    public static final long START_TIME = System.currentTimeMillis();
    private final MessageDelegatingHandler messageDelegatingHandler;
    private final ISessionService sessionService;
    private final ISubscriptionService subscriptionService;
    private final PublishHandler publishHandler;
    private final Serializer serializer;
    private final boolean enableSysTopic;
    private final int brokerId;
    //@formatter:on

    public BrokerHandler(MqttxConfig config, MessageDelegatingHandler messageDelegatingHandler,
                         ISessionService sessionService, ISubscriptionService subscriptionService,
                         PublishHandler publishHandler, @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") Serializer serializer) {
        Assert.notNull(messageDelegatingHandler, "messageDelegatingHandler can't be null");
        Assert.notNull(sessionService, "sessionService can't be null");
        Assert.notNull(subscriptionService, "subscriptionService can't be null");
        Assert.notNull(serializer, "serializer can't be null");

        MqttxConfig.SysTopic sysTopic = config.getSysTopic();
        this.messageDelegatingHandler = messageDelegatingHandler;
        this.sessionService = sessionService;
        this.subscriptionService = subscriptionService;
        this.publishHandler = publishHandler;
        this.serializer = serializer;
        this.enableSysTopic = sysTopic.getEnable();
        this.brokerId = config.getBrokerId();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        CHANNELS.add(ctx.channel());

        if (enableSysTopic) {
            // cas
            while (true) {
                int old = MAX_ACTIVE_SIZE.get();
                int now = CHANNELS.size();

                if (old >= now) {
                    break;
                }

                if (MAX_ACTIVE_SIZE.compareAndSet(old, now)) {
                    break;
                }
            }
        }
    }

    /**
     * 连接断开后进行如下操作:
     * <ol>
     *     <li>清理 {@link ConnectHandler#CLIENT_MAP} 中保存的 clientId 与 channelId 绑定关系</li>
     *     <li>遗嘱消息处理</li>
     *     <li>当 cleanSession = 0 时持久化 session,这样做的目的是保存 <code>Session#messageId</code> 字段变化</li>
     * </ol>
     * <p>
     * [MQTT-3.1.2-8]
     * If the Will Flag is set to 1 this indicates that, if the Connect request is accepted, a Will Message MUST be
     * stored on the Server and associated with the Network Connection. The Will Message MUST be published when the
     * Network Connection is subsequently closed unless the Will Message has been deleted by the Server on receipt of
     * a DISCONNECT Packet.
     *
     * @param ctx {@link ChannelHandlerContext}
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 获取当前会话
        Session session = (Session) ctx.channel().attr(AttributeKey.valueOf(Session.KEY)).get();

        // 会话状态处理
        if (session != null) {
            onClientDisconnected(ctx, session);
        }
    }

    /**
     * 客户端下线处理:
     * <ol>
     *     <li>遗嘱消息</li>
     *     <li>{@link Session} 处理</li>
     *     <li>下线通知</li>
     * </ol>
     *
     * @param ctx     {@link ChannelHandlerContext}
     * @param session 会话
     */
    private void onClientDisconnected(ChannelHandlerContext ctx, Session session) {
        final String clientId = session.getClientId();

        // 发布遗嘱消息
        Optional.of(session)
                .map(Session::getWillMessage)
                .ifPresent(msg -> {
                    // 解决遗嘱消息无法 retain 的 bug
                    if (msg.isRetain()) {
                        publishHandler.handleRetainMsg(msg);
                    }
                    publishHandler.publish(msg, ctx, false);
                });

        // session 处理
        ConnectHandler.CLIENT_MAP.remove(clientId);
        if (Boolean.TRUE.equals(session.getCleanSession())) {
            // 当 cleanSession = 1，清理会话状态。
            // MQTTX 为了提升性能，将 session/pub/pubRel 等信息保存在内存中，这部分信息关联 {@link io.netty.channel.Channel} 无需 clean 由 GC 自动回收.
            // 订阅信息则不同，此类信息通过常驻内存，需要明确调用清理的 API
            subscriptionService.clearClientSubscriptions(clientId, true);

            // 清理系统主题订阅
            if (enableSysTopic) {
                subscriptionService.clearClientSysSub(clientId);
            }
        } else {
            sessionService.save(session);
        }

        // 下线通知
        if (enableSysTopic) {
            final String topic = String.format(TopicUtils.BROKER_CLIENT_DISCONNECT, brokerId, clientId);
            List<ClientSub> clientSubs = subscriptionService.searchSysTopicClients(topic);
            if (CollectionUtils.isEmpty(clientSubs)) {
                return;
            }

            byte[] bytes = LocalDateTime.now().toString().getBytes(StandardCharsets.UTF_8);
            ByteBuf disconnectTime = Unpooled.buffer(bytes.length).writeBytes(bytes);
            MqttPublishMessage mpm = MqttMessageBuilders.publish()
                    .qos(MqttQoS.AT_MOST_ONCE)
                    .retained(false)
                    .topicName(topic)
                    .payload(disconnectTime)
                    .build();

            for (ClientSub clientSub : clientSubs) {
                Optional.ofNullable(ConnectHandler.CLIENT_MAP.get(clientSub.getClientId()))
                        .map(BrokerHandler.CHANNELS::find)
                        .ifPresent(channel -> channel.writeAndFlush(mpm.retain()));
            }

            // 必须最后释放
            mpm.release();
        }
    }

    /**
     * 修改用户授权的 pub&sub topic 列表
     *
     * @param clientId            客户端 ID
     * @param authorizedSubTopics 被授权订阅 topic 列表
     * @param authorizedPubTopics 被授权发布 topic 列表
     */
    private void alterUserAuthorizedTopic(String clientId, List<String> authorizedSubTopics, List<String> authorizedPubTopics) {
        if (CollectionUtils.isEmpty(authorizedPubTopics) && CollectionUtils.isEmpty(authorizedSubTopics)) {
            return;
        }

        Optional.ofNullable(ConnectHandler.CLIENT_MAP.get(clientId))
                .map(CHANNELS::find)
                .ifPresent(channel -> {
                    if (!CollectionUtils.isEmpty(authorizedPubTopics)) {
                        channel.attr(AttributeKey.valueOf(AbstractMqttSessionHandler.AUTHORIZED_PUB_TOPICS)).set(authorizedPubTopics);
                    }
                    if (!CollectionUtils.isEmpty(authorizedSubTopics)) {
                        channel.attr(AttributeKey.valueOf(AbstractMqttSessionHandler.AUTHORIZED_SUB_TOPICS)).set(authorizedSubTopics);
                    }
                });
    }

    /**
     * 异常处理及请求分发
     *
     * @param ctx         {@link ChannelHandlerContext}
     * @param mqttMessage {@link MqttMessage}
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage mqttMessage) {
        // 异常处理
        if (mqttMessage.decoderResult().isFailure()) {
            exceptionCaught(ctx, mqttMessage.decoderResult().cause());
            return;
        }

        // 消息处理
        messageDelegatingHandler.handle(ctx, mqttMessage);
    }

    /**
     * 异常处理
     *
     * @param ctx   {@link ChannelHandlerContext}
     * @param cause 异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 主要处理 connect 消息相关异常
        MqttConnAckMessage mqttConnAckMessage = null;
        if (cause instanceof MqttIdentifierRejectedException) {
            mqttConnAckMessage = MqttMessageBuilders.connAck()
                    .sessionPresent(false)
                    .returnCode(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED)
                    .build();
        } else if (cause instanceof MqttUnacceptableProtocolVersionException) {
            mqttConnAckMessage = MqttMessageBuilders.connAck()
                    .sessionPresent(false)
                    .returnCode(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION)
                    .build();
        } else if (cause instanceof AuthenticationException) {
            mqttConnAckMessage = MqttMessageBuilders.connAck()
                    .sessionPresent(false)
                    .returnCode(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD)
                    .build();
        } else if (cause instanceof AuthorizationException) {
            mqttConnAckMessage = MqttMessageBuilders.connAck()
                    .sessionPresent(false)
                    .returnCode(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED)
                    .build();

            log.info("client 权限异常:{}", cause.getMessage());
        } else if (cause instanceof IOException) {
            // 连接被强制断开
            Session session = (Session) ctx.channel().attr(AttributeKey.valueOf(Session.KEY)).get();
            if (session != null) {
                log.error("client:{} 连接被强制断开", session.getClientId());
            }
        } else {
            log.error("未知异常", cause);
        }

        if (mqttConnAckMessage != null) {
            ctx.writeAndFlush(mqttConnAckMessage);
        }
        ctx.close();
    }

    /**
     * 心跳、握手事件处理
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param evt {@link IdleStateEvent}
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        String host = socketAddress.getAddress().getHostAddress();
        int port = socketAddress.getPort();

        if (evt instanceof IdleStateEvent) {
            if (IdleState.ALL_IDLE.equals(((IdleStateEvent) evt).state())) {
                log.info("R[{}:{}] 心跳超时", host, port);

                // 关闭连接
                ctx.close();
            }
        } else if (evt instanceof SslHandshakeCompletionEvent) {
            // 监听 ssl 握手事件
            if (!((SslHandshakeCompletionEvent) evt).isSuccess()) {
                log.warn("R[{}:{}] ssl 握手失败", host, port);
                ctx.close();
            }
        }
    }

    /**
     * 行为：
     * <ol>
     *     <li>修改用户权限</li>
     * </ol>
     *
     * @param msg 集群消息
     */
    @Override
    public void action(byte[] msg) {
        InternalMessage<Authentication> im;
        if (serializer instanceof JsonSerializer) {
            im = ((JsonSerializer) serializer).deserialize(msg, new TypeReference<InternalMessage<Authentication>>() {
            });
        } else {
            //noinspection unchecked
            im = serializer.deserialize(msg, InternalMessage.class);
        }
        Authentication data = im.getData();
        // 目的是为了兼容 v1.0.2(含) 之前的版本
        String clientId = StringUtils.isEmpty(data.getClientId()) ? data.getUsername() : data.getClientId();
        List<String> authorizedPub = data.getAuthorizedPub();
        List<String> authorizedSub = data.getAuthorizedSub();
        if (StringUtils.isEmpty(clientId) || (CollectionUtils.isEmpty(authorizedPub) && CollectionUtils.isEmpty(authorizedSub))) {
            log.info("权限修改参数非法:{}", im);
            return;
        }
        alterUserAuthorizedTopic(clientId, authorizedSub, authorizedPub);

        // 移除 cache&redis 中客户端订阅的 topic
        if (!CollectionUtils.isEmpty(authorizedSub)) {
            subscriptionService.clearUnAuthorizedClientSub(clientId, authorizedSub);
        }
    }

    @Override
    public boolean support(String channel) {
        return InternalMessageEnum.ALTER_USER_AUTHORIZED_TOPICS.getChannel().equals(channel);
    }
}
