package com.jun.mqttx.broker.handler;

import com.jun.mqttx.broker.BrokerHandler;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.constants.InternalMessageEnum;
import com.jun.mqttx.entity.Authentication;
import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.entity.Session;
import com.jun.mqttx.service.*;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

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
@Handler(type = CONNECT)
public final class ConnectHandler extends AbstractMqttTopicSecureHandler {

    /**
     * 初始化10000长连接客户端
     */
    public final static ConcurrentHashMap<String, ChannelId> clientMap = new ConcurrentHashMap<>(10000);
    private static final String NONE_ID_PREFIX = "NONE_ID_";
    final private Boolean enableCluster;

    final private Boolean enableTopicSubPubSecure;
    private int brokerId;
    /**
     * 认证服务
     */
    private IAuthenticationService authenticationService;

    /**
     * 会话服务
     */
    private ISessionService sessionService;

    /**
     * 主题订阅相关服务
     */
    private ISubscriptionService subscriptionService;

    /**
     * publish 消息服务
     */
    private IPublishMessageService publishMessageService;

    /**
     * pubRel 消息服务
     */
    private IPubRelMessageService pubRelMessageService;

    private IInternalMessagePublishService internalMessagePublishService;

    public ConnectHandler(IAuthenticationService authenticationService, ISessionService sessionService,
                          ISubscriptionService subscriptionService, IPublishMessageService publishMessageService,
                          IPubRelMessageService pubRelMessageService, MqttxConfig mqttxConfig, @Nullable IInternalMessagePublishService internalMessagePublishService) {
        Assert.notNull(authenticationService, "authentication can't be null");
        Assert.notNull(sessionService, "sessionService can't be null");
        Assert.notNull(subscriptionService, "subscriptionService can't be null");
        Assert.notNull(publishMessageService, "publishMessageService can't be null");
        Assert.notNull(pubRelMessageService, "pubRelMessageService can't be null");
        Assert.notNull(mqttxConfig, "mqttxConfig can't be null");

        MqttxConfig.Cluster cluster = mqttxConfig.getCluster();
        this.authenticationService = authenticationService;
        this.sessionService = sessionService;
        this.subscriptionService = subscriptionService;
        this.publishMessageService = publishMessageService;
        this.pubRelMessageService = pubRelMessageService;
        this.enableCluster = cluster.getEnable();
        this.brokerId = mqttxConfig.getBrokerId();
        this.enableTopicSubPubSecure = mqttxConfig.getEnableTopicSubPubSecure();

        if (enableCluster) {
            this.internalMessagePublishService = internalMessagePublishService;
            Assert.notNull(internalMessagePublishService, "internalMessagePublishService can't be null");
        }
    }

    /**
     * 客户端连接请求处理，流程如下：
     * <ol>
     *     <li>连接校验</li>
     *     <li>处理 clientId 关联的连接 </li>
     *     <li>返回响应报文</li>
     *     <li>心跳检测</li>
     *     <li>Qos1\Qos2 消息处理</li>
     * </ol>
     *
     * @param ctx 见 {@link ChannelHandlerContext}
     * @param msg 解包后的数据
     */
    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        //获取identifier,password
        MqttConnectMessage mcm = (MqttConnectMessage) msg;
        MqttConnectVariableHeader variableHeader = mcm.variableHeader();
        MqttConnectPayload payload = mcm.payload();

        //用户名及密码校验
        Authentication auth = null;
        if (variableHeader.hasPassword() && variableHeader.hasUserName()) {
            auth = authenticationService.authenticate(payload.userName(), payload.passwordInBytes());
        }

        //获取clientId
        String clientId = mcm.payload().clientIdentifier();
        if (StringUtils.isEmpty(clientId)) {
            //[MQTT-3.1.3-8] If the Client supplies a zero-byte ClientId with CleanSession set to 0, the Server MUST
            // respond to the CONNECT Packet with a CONNACK return code 0x02 (Identifier rejected) and then close the
            // Network Connection.
            if (!variableHeader.isCleanSession()) {
                throw new MqttIdentifierRejectedException("Violation: zero-byte ClientId with CleanSession set to 0");
            }

            //broker  生成一个唯一ID
            //[MQTT-3.1.3-6] A Server MAY allow a Client to supply a ClientId that has a length of zero bytes,
            //however if it does so the Server MUST treat this as a special case and assign a unique ClientId to that Client.
            //It MUST then process the CONNECT packet as if the Client had provided that unique ClientId
            clientId = genClientId();
        }

        //关闭之前可能存在的tcp链接
        //[MQTT-3.1.4-2] If the ClientId represents a Client already connected to the Server then the Server MUST
        //disconnect the existing Client
        if (enableCluster) {
            internalMessagePublishService.publish(
                    new InternalMessage<>(clientId, System.currentTimeMillis(), brokerId),
                    InternalMessageEnum.DISCONNECT.getChannel()
            );
        }
        Optional.ofNullable(clientMap.get(clientId))
                .map(BrokerHandler.channels::find)
                .filter(channel -> !Objects.equals(channel, ctx.channel()))
                .ifPresent(ChannelOutboundInvoker::close);

        //会话状态的处理
        //[MQTT-3.1.3-7] If the Client supplies a zero-byte ClientId, the Client MUST also set CleanSession to 1. -
        //这部分是 client 遵守的规则
        //[MQTT-3.1.2-6] State data associated with this Session MUST NOT be reused in any subsequent Session - 针对
        //clearSession == 1 的情况，需要清理之前保存的会话状态
        boolean clearSession = variableHeader.isCleanSession();
        if (clearSession) {
            actionOnCleanSession(clientId);
        }

        //新建会话并保存会话，同时判断sessionPresent
        Session session;
        boolean sessionPresent;
        if (clearSession) {
            sessionPresent = false;
            session = Session.of(clientId, true);
        } else {
            sessionPresent = sessionService.hasKey(clientId);
            if (sessionPresent) {
                session = sessionService.find(clientId);
            } else {
                session = Session.of(clientId, false);
                sessionService.save(session);
            }
        }
        clientMap.put(clientId, ctx.channel().id());
        saveSessionWithChannel(ctx, session);
        if (enableTopicSubPubSecure) {
            saveAuthorizedTopics(ctx, auth);
        }

        //处理遗嘱消息
        //[MQTT-3.1.2-8] If the Will Flag is set to 1 this indicates that, if the Connect request is accepted, a Will
        // Message MUST be stored on the Server and associated with the Network Connection. The Will Message MUST be
        // published when the Network Connection is subsequently closed unless the Will Message has been deleted by the
        // Server on receipt of a DISCONNECT Packet.
        boolean willFlag = variableHeader.isWillFlag();
        if (willFlag) {
            MqttPublishMessage mqttPublishMessage = MqttMessageBuilders.publish()
                    .messageId(nextMessageId(ctx))
                    .retained(variableHeader.isWillRetain())
                    .topicName(payload.willTopic())
                    .payload(Unpooled.wrappedBuffer(payload.willMessageInBytes()))
                    .qos(MqttQoS.valueOf(variableHeader.willQos()))
                    .build();
            session.setWillMessage(mqttPublishMessage);
        }

        //返回连接响应
        MqttConnAckMessage acceptAck = MqttMessageBuilders.connAck()
                .sessionPresent(sessionPresent)
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .build();
        ctx.writeAndFlush(acceptAck);

        //心跳超时设置
        //[MQTT-3.1.2-24] If the Keep Alive value is non-zero and the Server does not receive a Control Packet from
        //the Client within one and a half times the Keep Alive time period, it MUST disconnect the Network Connection
        //to the Client as if the network had failed
        double heartbeat = variableHeader.keepAliveTimeSeconds() * 1.5;
        if (heartbeat > 0) {
            //替换掉 NioChannelSocket 初始化时加入的 idleHandler
            ctx.pipeline().replace(IdleStateHandler.class, "idleHandler", new IdleStateHandler(
                    0, 0, (int) heartbeat));
        }

        //根据协议补发 qos1,与 qos2 的消息
        if (!clearSession) {
            List<PubMsg> pubMsgList = publishMessageService.search(clientId);
            pubMsgList.forEach(pubMsg -> {
                String topic = pubMsg.getTopic();
                //订阅权限判定
                if (enableTopicSubPubSecure && !hasAuthToSubTopic(ctx, topic)) {
                    return;
                }

                MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.PUBLISH, true, MqttQoS.valueOf(pubMsg.getQoS()), pubMsg.isRetain(), 0),
                        new MqttPublishVariableHeader(topic, pubMsg.getMessageId()),
                        //这是一个浅拷贝，任何对pubMsg中payload的修改都会反馈到wrappedBuffer
                        Unpooled.wrappedBuffer(pubMsg.getPayload())
                );

                if (enableCluster) {
                    //集群消息发布
                    internalMessagePublishService.publish(
                            new InternalMessage<>(pubMsg, System.currentTimeMillis(), brokerId),
                            InternalMessageEnum.PUB.getChannel()
                    );
                }

                ctx.writeAndFlush(mqttMessage);
            });

            List<Integer> pubRelList = pubRelMessageService.search(clientId);
            pubRelList.forEach(messageId -> {
                MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                        //pubRel 的fixHeader 是固定死了的 [0,1,1,0,0,0,1,0]
                        new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_MOST_ONCE, false, 0),
                        MqttMessageIdVariableHeader.from(messageId),
                        null
                );

                ctx.writeAndFlush(mqttMessage);
            });
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
     * 当 conn cleanSession = 1,清理会话状态.会话状态有如下状态组成:
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
    void actionOnCleanSession(String clientId) {
        sessionService.clear(clientId);
        subscriptionService.clearClientSubscriptions(clientId);
        publishMessageService.clear(clientId);
        pubRelMessageService.clear(clientId);
    }
}