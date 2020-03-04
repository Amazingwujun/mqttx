package com.jun.mqttx.server;

import com.jun.mqttx.entity.Session;
import com.jun.mqttx.server.handler.MessageDelegatingHandler;
import com.jun.mqttx.service.ISessionService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Optional;

/**
 * broker handler
 *
 * @author Jun
 * @date 2020-03-03 21:30
 */
@Slf4j
@ChannelHandler.Sharable
@Component
public class BrokerHandler extends SimpleChannelInboundHandler<MqttMessage> {

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

    public BrokerHandler(MessageDelegatingHandler messageDelegatingHandler, ISessionService sessionService) {
        Assert.notNull(messageDelegatingHandler, "messageDelegatingHandler can't be null");
        Assert.notNull(sessionService, "sessionService can't be null");

        this.messageDelegatingHandler = messageDelegatingHandler;
        this.sessionService = sessionService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        channels.add(ctx.channel());
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage mqttMessage) {
        //异常处理
        if (mqttMessage.decoderResult().isFailure()) {
            onDecodeFailure(ctx, mqttMessage.decoderResult().cause());
            return;
        }

        //消息处理
        messageDelegatingHandler.handle(ctx, mqttMessage);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("未知异常", cause);
    }

    /**
     * 处理未通过 {@link MqttMessageType#DISCONNECT} 消息断开连接的客户端的遗嘱消息.
     * <p>
     * [MQTT-3.1.2-8]
     * If the Will Flag is set to 1 this indicates that, if the Connect request is accepted, a Will Message MUST be
     * stored on the Server and associated with the Network Connection. The Will Message MUST be published when the
     * Network Connection is subsequently closed unless the Will Message has been deleted by the Server on receipt of
     * a DISCONNECT Packet.
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param evt {@link IdleStateEvent}
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            if (IdleState.ALL_IDLE.equals(((IdleStateEvent) evt).state())) {
                //获取当前会话
                String clientId = (String) ctx.channel().attr(AttributeKey.valueOf("clientId")).get();
                Session session = sessionService.find(clientId);

                //发布遗嘱消息
                Optional.ofNullable(session)
                        .map(Session::getWillMessage)
                        .ifPresent(msg -> messageDelegatingHandler.handle(ctx, msg));

                //关闭连接
                ctx.close();
            }
        }
    }

    /**
     * 消息解码异常处理
     *
     * @param ctx   {@link ChannelHandlerContext}
     * @param cause mqtt消息解码异常
     */
    private void onDecodeFailure(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof MqttUnacceptableProtocolVersionException) {
            //mqtt版本异常
            MqttMessage mqttMessage = MqttMessageBuilders.connAck()
                    .returnCode(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION)
                    .sessionPresent(false)
                    .build();

            ctx.writeAndFlush(mqttMessage);
        }
        if (cause instanceof MqttIdentifierRejectedException) {
            MqttMessage mqttMessage = MqttMessageBuilders.connAck()
                    .returnCode(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED)
                    .sessionPresent(false)
                    .build();
            ctx.writeAndFlush(mqttMessage);
        }

        ctx.close();
    }
}
