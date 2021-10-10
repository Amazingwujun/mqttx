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

import com.jun.mqttx.broker.BrokerHandler;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.constants.InternalMessageEnum;
import com.jun.mqttx.entity.*;
import com.jun.mqttx.exception.AuthenticationException;
import com.jun.mqttx.service.*;
import com.jun.mqttx.utils.GlobalExecutor;
import com.jun.mqttx.utils.TopicUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static io.netty.handler.codec.mqtt.MqttMessageType.CONNECT;

/**
 * {@link io.netty.handler.codec.mqtt.MqttMessageType#CONNECT} 消息处理器
 *
 * @author Jun
 * @since 1.0.4
 */
@Slf4j
@Handler(type = CONNECT)
public final class ConnectHandler extends AbstractMqttTopicSecureHandler {
    //@formatter:off

    public final static ConcurrentHashMap<String, ChannelId> CLIENT_MAP = new ConcurrentHashMap<>(100000);
    private static final String NONE_ID_PREFIX = "NONE_ID_";
    final private boolean enableTopicSubPubSecure, enableSysTopic;
    private final int brokerId;
    /** 认证服务 */
    private final IAuthenticationService authenticationService;
    /** 会话服务 */
    private final ISessionService sessionService;
    /** 主题订阅相关服务 */
    private final ISubscriptionService subscriptionService;
    /** publish 消息服务 */
    private final IPublishMessageService publishMessageService;
    /** pubRel 消息服务 */
    private final IPubRelMessageService pubRelMessageService;
    /** 内部消息发布服务 */
    private IInternalMessagePublishService internalMessagePublishService;

    //@formatter:on

    public ConnectHandler(IAuthenticationService authenticationService, ISessionService sessionService,
                          ISubscriptionService subscriptionService, IPublishMessageService publishMessageService,
                          IPubRelMessageService pubRelMessageService, MqttxConfig config, @Nullable IInternalMessagePublishService internalMessagePublishService) {
        super(config.getEnableTestMode(), config.getCluster().getEnable());
        Assert.notNull(authenticationService, "authentication can't be null");
        Assert.notNull(sessionService, "sessionService can't be null");
        Assert.notNull(subscriptionService, "subscriptionService can't be null");
        Assert.notNull(publishMessageService, "publishMessageService can't be null");
        Assert.notNull(pubRelMessageService, "pubRelMessageService can't be null");
        Assert.notNull(config, "mqttxConfig can't be null");

        MqttxConfig.SysTopic sysTopic = config.getSysTopic();
        brokerId = config.getBrokerId();
        this.authenticationService = authenticationService;
        this.sessionService = sessionService;
        this.subscriptionService = subscriptionService;
        this.publishMessageService = publishMessageService;
        this.pubRelMessageService = pubRelMessageService;
        this.enableTopicSubPubSecure = config.getEnableTopicSubPubSecure();
        this.enableSysTopic = sysTopic.getEnable();

        if (isClusterMode()) {
            this.internalMessagePublishService = internalMessagePublishService;
            Assert.notNull(internalMessagePublishService, "internalMessagePublishService can't be null");
        }
    }

    /**
     * 客户端连接请求处理
     *
     * @param ctx 见 {@link ChannelHandlerContext}
     * @param msg 解包后的数据
     */
    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        // 获取identifier,password
        MqttConnectMessage mcm = (MqttConnectMessage) msg;
        MqttConnectVariableHeader variableHeader = mcm.variableHeader();
        MqttConnectPayload payload = mcm.payload();
        final String username = payload.userName();
        final byte[] password = payload.passwordInBytes();

        // 获取clientId
        var clientIdentifier = payload.clientIdentifier();
        if (ObjectUtils.isEmpty(clientIdentifier)) {
            // [MQTT-3.1.3-8] If the Client supplies a zero-byte ClientId with CleanSession set to 0, the Server MUST
            // respond to the CONNECT Packet with a CONNACK return code 0x02 (Identifier rejected) and then close the
            // Network Connection.
            if (!variableHeader.isCleanSession()) {
                throw new MqttIdentifierRejectedException("Violation: zero-byte ClientId with CleanSession set to 0");
            }

            // broker  生成一个唯一ID
            // [MQTT-3.1.3-6] A Server MAY allow a Client to supply a ClientId that has a length of zero bytes,
            // however if it does so the Server MUST treat this as a special case and assign a unique ClientId to that Client.
            // It MUST then process the CONNECT packet as if the Client had provided that unique ClientId
            clientIdentifier = genClientId();
        }
        final var clientId = clientIdentifier; // for lambda

        // 用户名及密码校验
        if (variableHeader.hasPassword() && variableHeader.hasUserName()) {
            authenticationService.asyncAuthenticate(ClientAuthDTO.of(clientId, username, password))
                    .thenAccept(authentication -> {
                        if (authentication == null) {
                            // authentication 是有可能为 null 的
                            // 比如 认证服务 response http status = 200, 但是响应内容为空
                            authentication = Authentication.of(clientId);
                        } else {
                            authentication.setClientId(clientId); // 置入 clientId
                        }
                        process0(ctx, msg, authentication);
                    })
                    .exceptionally(throwable -> {
                        MqttConnAckMessage mqttConnAckMessage;
                        if (throwable.getCause() instanceof AuthenticationException) {
                            mqttConnAckMessage = MqttMessageBuilders.connAck()
                                    .sessionPresent(false)
                                    .returnCode(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD)
                                    .build();
                        } else {
                            mqttConnAckMessage = MqttMessageBuilders.connAck()
                                    .sessionPresent(false)
                                    .returnCode(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE)
                                    .build();
                        }
                        log.error(String.format("client[id: %s, username: %s]登入失败", clientId, username), throwable);

                        // 告知客户端并关闭连接
                        ctx.writeAndFlush(mqttConnAckMessage);
                        ctx.close();
                        return null;
                    });
        } else {
            process0(ctx, msg, Authentication.of(clientId));
        }
    }

    /**
     * 客户端连接请求处理，流程如下：
     * <ol>
     *     <li>处理 clientId 关联的连接 </li>
     *     <li>返回响应报文</li>
     *     <li>心跳检测</li>
     *     <li>Qos1\Qos2 消息处理</li>
     * </ol>
     *
     * @param ctx  见 {@link ChannelHandlerContext}
     * @param msg  解包后的数据
     * @param auth 认证对象，见 {@link Authentication}, 该对象可能不允许为空
     */
    private void process0(ChannelHandlerContext ctx, MqttMessage msg, Authentication auth) {
        MqttConnectMessage mcm = (MqttConnectMessage) msg;
        MqttConnectVariableHeader variableHeader = mcm.variableHeader();
        MqttConnectPayload payload = mcm.payload();

        // 获取clientId
        var clientId = auth.getClientId();

        // 关闭之前可能存在的tcp链接
        // [MQTT-3.1.4-2] If the ClientId represents a Client already connected to the Server then the Server MUST
        // disconnect the existing Client
        if (CLIENT_MAP.containsKey(clientId)) {
            Optional.ofNullable(CLIENT_MAP.get(clientId))
                    .map(BrokerHandler.CHANNELS::find)
                    .filter(channel -> !Objects.equals(channel, ctx.channel()))
                    .ifPresent(ChannelOutboundInvoker::close);
        } else if (isClusterMode()) {
            internalMessagePublishService.publish(
                    new InternalMessage<>(clientId, System.currentTimeMillis(), brokerId),
                    InternalMessageEnum.DISCONNECT.getChannel()
            );
        }

        // 会话状态的处理
        // [MQTT-3.1.3-7] If the Client supplies a zero-byte ClientId, the Client MUST also set CleanSession to 1. -
        // 这部分是 client 遵守的规则
        // [MQTT-3.1.2-6] State data associated with this Session MUST NOT be reused in any subsequent Session - 针对
        // clearSession == 1 的情况，需要清理之前保存的会话状态
        boolean isCleanSession = variableHeader.isCleanSession();
        if (isCleanSession) {
            actionOnCleanSession(clientId);
        }

        // 新建会话并保存会话，同时判断sessionPresent
        Session session;
        boolean sessionPresent = false;
        if (isCleanSession) {
            session = Session.of(clientId, true);
        } else {
            session = sessionService.find(clientId);
            if (session == null) {
                session = Session.of(clientId, false);
                sessionService.save(session);
            } else {
                sessionPresent = true;
            }
        }
        CLIENT_MAP.put(clientId, ctx.channel().id());
        saveSessionWithChannel(ctx, session);
        if (enableTopicSubPubSecure) {
            saveAuthorizedTopics(ctx, auth);
        }

        // 处理遗嘱消息
        // [MQTT-3.1.2-8] If the Will Flag is set to 1 this indicates that, if the Connect request is accepted, a Will
        // Message MUST be stored on the Server and associated with the Network Connection. The Will Message MUST be
        // published when the Network Connection is subsequently closed unless the Will Message has been deleted by the
        // Server on receipt of a DISCONNECT Packet.
        boolean willFlag = variableHeader.isWillFlag();
        if (willFlag) {
            PubMsg pubMsg = PubMsg.of(variableHeader.willQos(), payload.willTopic(),
                    variableHeader.isWillRetain(), payload.willMessageInBytes());
            pubMsg.setWillFlag(true);
            session.setWillMessage(pubMsg);
        }

        // 返回连接响应
        MqttConnAckMessage acceptAck = MqttMessageBuilders.connAck()
                .sessionPresent(sessionPresent)
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .build();
        ctx.writeAndFlush(acceptAck);

        // 连接成功相关处理
        onClientConnectSuccess(ctx);

        // 心跳超时设置
        // [MQTT-3.1.2-24] If the Keep Alive value is non-zero and the Server does not receive a Control Packet from
        // the Client within one and a half times the Keep Alive time period, it MUST disconnect the Network Connection
        // to the Client as if the network had failed
        double heartbeat = variableHeader.keepAliveTimeSeconds() * 1.5;
        if (heartbeat > 0) {
            // 替换掉 NioChannelSocket 初始化时加入的 idleHandler
            ctx.pipeline().replace(IdleStateHandler.class, "idleHandler", new IdleStateHandler(
                    0, 0, (int) heartbeat));
        }

        // 根据协议补发 qos1,与 qos2 的消息
        if (!isCleanSession) {
            GlobalExecutor.execute("pub 消息补发任务", () -> republish(ctx));
        }
    }

    /**
     * 客户端(cleanSession = false)上线，补发 qos1,2 消息
     *
     * @param ctx {@link ChannelHandlerContext}
     */
    private void republish(ChannelHandlerContext ctx) {
        final String clientId = clientId(ctx);
        List<PubMsg> pubMsgList = publishMessageService.search(clientId);
        pubMsgList.forEach(pubMsg -> {
            // fixedHeader dup flag 置为 true
            pubMsg.setDup(true);

            String topic = pubMsg.getTopic();
            // 订阅权限判定
            if (enableTopicSubPubSecure && !hasAuthToSubTopic(ctx, topic)) {
                return;
            }

            MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.PUBLISH, true, MqttQoS.valueOf(pubMsg.getQoS()), false, 0),
                    new MqttPublishVariableHeader(topic, pubMsg.getMessageId()),
                    // 这是一个浅拷贝，任何对pubMsg中payload的修改都会反馈到wrappedBuffer
                    Unpooled.wrappedBuffer(pubMsg.getPayload())
            );

            ctx.writeAndFlush(mqttMessage);
        });

        List<Integer> pubRelList = pubRelMessageService.searchOut(clientId);
        pubRelList.forEach(messageId -> {
            MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                    // pubRel 的 fixHeader 是固定死了的 [0,1,1,0,0,0,1,0]
                    new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                    MqttMessageIdVariableHeader.from(messageId),
                    null
            );

            ctx.writeAndFlush(mqttMessage);
        });
    }

    /**
     * 客户端连接成功处理
     *
     * @param ctx {@link ChannelHandlerContext}
     */
    private void onClientConnectSuccess(ChannelHandlerContext ctx) {
        if (enableSysTopic) {
            final String topic = String.format(TopicUtils.BROKER_CLIENT_CONNECT, brokerId, clientId(ctx));

            // 抓取订阅了该主题的客户
            List<ClientSub> clientSubs = subscriptionService.searchSysTopicClients(topic);
            if (CollectionUtils.isEmpty(clientSubs)) {
                return;
            }

            // publish 对象组装
            byte[] bytes = LocalDateTime.now().toString().getBytes(StandardCharsets.UTF_8);
            ByteBuf connectTime = Unpooled.buffer(bytes.length).writeBytes(bytes);
            MqttPublishMessage mpm = MqttMessageBuilders.publish()
                    .qos(MqttQoS.AT_MOST_ONCE)
                    .retained(false)
                    .topicName(topic)
                    //  上线时间
                    .payload(connectTime)
                    .build();

            // 消息发布
            for (ClientSub clientSub : clientSubs) {
                Optional
                        .ofNullable(CLIENT_MAP.get(clientSub.getClientId()))
                        .map(BrokerHandler.CHANNELS::find)
                        .ifPresent(channel -> {
                            channel.writeAndFlush(mpm.retain());
                        });
            }

            // 必须最后释放
            mpm.release();
        }
    }

    /**
     * 生成一个唯一ID
     *
     * @return Unique Id
     */
    private String genClientId() {
        return NONE_ID_PREFIX + brokerId + "_" + System.currentTimeMillis();
    }

    /**
     * 当 conn cleanSession = 1,清理会话状态.会话状态由下列状态组成:
     * <ul>
     *     <li>The existence of a Session, even if the rest of the Session state is empty.</li>
     *     <li>The Client’s subscriptions.</li>
     *     <li>QoS 1 and QoS 2 messages which have been sent to the Client, but have not been completely acknowledged.</li>
     *     <li>QoS 1 and QoS 2 messages pending transmission to the Client.</li>
     *     <li>QoS 2 messages which have been received from the Client, but have not been completely acknowledged.</li>
     *     <li>Optionally, QoS 0 messages pending transmission to the Client.</li>
     * </ul>
     *
     * @param clientId 客户ID
     */
    private void actionOnCleanSession(String clientId) {
        // sessionService.clear(String ClientId) 方法返回 true 则表明该 clientId 之前的 cleanSession = false, 那么应该继续清理
        // 订阅信息、pub 信息、 pubRel 信息, 否则无需清理
        if (sessionService.clear(clientId)) {
            subscriptionService.clearClientSubscriptions(clientId, false);
            publishMessageService.clear(clientId);
            pubRelMessageService.clear(clientId);
        }
    }
}
