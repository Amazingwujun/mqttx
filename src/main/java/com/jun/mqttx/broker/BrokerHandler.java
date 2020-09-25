package com.jun.mqttx.broker;

import com.alibaba.fastjson.JSONObject;
import com.jun.mqttx.broker.handler.AbstractMqttSessionHandler;
import com.jun.mqttx.broker.handler.ConnectHandler;
import com.jun.mqttx.broker.handler.MessageDelegatingHandler;
import com.jun.mqttx.constants.InternalMessageEnum;
import com.jun.mqttx.consumer.Watcher;
import com.jun.mqttx.entity.Authentication;
import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.entity.Session;
import com.jun.mqttx.exception.AuthenticationException;
import com.jun.mqttx.exception.AuthorizationException;
import com.jun.mqttx.service.ISessionService;
import com.jun.mqttx.service.ISubscriptionService;
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
import java.util.List;
import java.util.Optional;

/**
 * broker handler
 *
 * @author Jun
 * @since 1.0.4
 */
@Slf4j
@ChannelHandler.Sharable
@Component
public class BrokerHandler extends SimpleChannelInboundHandler<MqttMessage> implements Watcher<Object> {

    /**
     * channel 群组
     */
    public static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    /**
     * 消息处理器
     */
    private MessageDelegatingHandler messageDelegatingHandler;

    /**
     * 会话服务
     */
    private ISessionService sessionService;

    private ISubscriptionService subscriptionService;

    public BrokerHandler(MessageDelegatingHandler messageDelegatingHandler, ISessionService sessionService,ISubscriptionService subscriptionService) {
        Assert.notNull(messageDelegatingHandler, "messageDelegatingHandler can't be null");
        Assert.notNull(sessionService, "sessionService can't be null");
        Assert.notNull(subscriptionService,"subscriptionService can't be null");

        this.messageDelegatingHandler = messageDelegatingHandler;
        this.sessionService = sessionService;
        this.subscriptionService = subscriptionService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        channels.add(ctx.channel());
    }

    /**
     * 连接断开后进行如下操作:
     * <ol>
     *     <li>清理 {@link ConnectHandler#clientMap} 中保存的 clientId 与 channelId 绑定关系</li>
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
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        //获取当前会话
        Session session = (Session) ctx.channel().attr(AttributeKey.valueOf(Session.KEY)).get();

        //会话状态处理
        if (session != null) {
            //发布遗嘱消息
            Optional.of(session)
                    .map(Session::getWillMessage)
                    .ifPresent(msg -> messageDelegatingHandler.handle(ctx, msg));

            ConnectHandler.clientMap.remove(session.getClientId());
            if (Boolean.FALSE.equals(session.getCleanSession())) {
                sessionService.save(session);
            }
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

        Optional.ofNullable(ConnectHandler.clientMap.get(clientId))
                .map(channels::find)
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
        //异常处理
        if (mqttMessage.decoderResult().isFailure()) {
            exceptionCaught(ctx, mqttMessage.decoderResult().cause());
            return;
        }

        //消息处理
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
        //主要处理 connect 消息相关异常
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
            //连接被强制断开
            Session session = (Session) ctx.channel().attr(AttributeKey.valueOf(Session.KEY)).get();
            if (session != null) {
                log.error("client:{} 连接出现异常", session.getClientId());
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
     * 处理未通过 {@link MqttMessageType#DISCONNECT} 消息断开连接的客户端的遗嘱消息.
     * <p>
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param evt {@link IdleStateEvent}
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            if (IdleState.ALL_IDLE.equals(((IdleStateEvent) evt).state())) {
                //关闭连接
                ctx.close();
            }
        } else if (evt instanceof SslHandshakeCompletionEvent) {
            // 监听 ssl 握手事件
            if (!((SslHandshakeCompletionEvent) evt).isSuccess()) {
                InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
                log.warn("ssl 握手失败，客户端ip:{}", socketAddress.getHostName());
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
     * @param im {@see InternalMessage}
     */
    @Override
    public void action(InternalMessage<Object> im) {
        if (im.getData() instanceof JSONObject) {
            Authentication data = ((JSONObject) im.getData()).toJavaObject(Authentication.class);
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
                subscriptionService.clearClientSub(clientId, authorizedSub);
            }
        }
    }

    @Override
    public boolean support(String channel) {
        return InternalMessageEnum.ALTER_USER_AUTHORIZED_TOPICS.getChannel().equals(channel);
    }
}