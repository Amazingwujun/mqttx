package com.jun.mqttx.server.handler;

import com.jun.mqttx.entity.Session;
import com.jun.mqttx.exception.AuthenticationException;
import com.jun.mqttx.server.BrokerHandler;
import com.jun.mqttx.service.IAuthenticationService;
import com.jun.mqttx.service.ISessionService;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.handler.codec.mqtt.MqttMessageType.CONNECT;

/**
 * {@link io.netty.handler.codec.mqtt.MqttMessageType#CONNECT} 消息处理器
 *
 * @author Jun
 * @date 2020-03-03 22:17
 */
@Component
public final class ConnectHandler implements MqttMessageHandler {

    private static final String NONE_ID_PREFIX = "NONE_";

    /**
     * 初始化10000长连接客户端
     */
    private final ConcurrentHashMap<String, ChannelId> clientMap = new ConcurrentHashMap<>(10000);

    /**
     * 认证服务
     */
    private IAuthenticationService authenticationService;

    /**
     * 会话服务
     */
    private ISessionService sessionService;

    public ConnectHandler(IAuthenticationService authenticationService, ISessionService sessionService) {
        Assert.notNull(authenticationService, "authentication can't be null");
        Assert.notNull(sessionService, "sessionService can't be null");

        this.authenticationService = authenticationService;
        this.sessionService = sessionService;
    }

    /**
     * 客户端连接请求处理，流程如下：
     * <ol>
     *     <li>用户名及密码校验</li>
     *     <li>clientId 处理</li>
     *     <li>keepalive 处理</li>
     *     <li>会话处理(包括之前的TCP连接处理)</li>
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
        if (variableHeader.hasPassword() && variableHeader.hasUserName()) {
            try {
                authenticationService.authenticate(payload.userName(), payload.passwordInBytes());
            } catch (AuthenticationException e) {
                MqttMessage mqttMessage = MqttMessageBuilders.connAck()
                        .returnCode(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD)
                        .build();
                ctx.writeAndFlush(mqttMessage);
                ctx.close();
                return;
            }
        }

        //获取clientId
        String clientId = mcm.payload().clientIdentifier();
        if (StringUtils.isEmpty(clientId)) {
            //broker  生成一个唯一ID
            //[MQTT-3.1.3-6] A Server MAY allow a Client to supply a ClientId that has a length of zero bytes,
            //however if it does so the Server MUST treat this as a special case and assign a unique ClientId to that Client.
            //It MUST then process the CONNECT packet as if the Client had provided that unique ClientId
            clientId = genClientId();
        }

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

        //关闭之前可能存在的tcp链接
        //[MQTT-3.1.4-2] If the ClientId represents a Client already connected to the Server then the Server MUST
        //disconnect the existing Client
        //todo 对集群来说，这里可能需要通知其它 broker 以断开之前的连接
        Optional.ofNullable(clientMap.get(clientId))
                .map(BrokerHandler.channels::find)
                .ifPresent(ChannelOutboundInvoker::close);

        //会话状态的处理
        //[MQTT-3.1.2-6]  State data associated with this Session MUST NOT be reused in any subsequent Session - 针对
        //clearSession == 1 的情况，需要清理之前保存的会话状态
        boolean clearSession = variableHeader.isCleanSession();
        if (clearSession) {
            //todo 清理掉与会话相关的数据
            //The existence of a Session, even if the rest of the Session state is empty.
            //The Client’s subscriptions.
            //QoS 1 and QoS 2 messages which have been sent to the Client, but have not been completely acknowledged.
            //QoS 1 and QoS 2 messages pending transmission to the Client.
            //QoS 2 messages which have been received from the Client, but have not been completely acknowledged.
            //Optionally, QoS 0 messages pending transmission to the Client.
            sessionService.clear(clientId);
        }

        //新建会话
        Session session = new Session();
        session.setClientId(clientId);

        //处理遗嘱消息
        //[MQTT-3.1.2-8] If the Will Flag is set to 1 this indicates that, if the Connect request is accepted, a Will
        // Message MUST be stored on the Server and associated with the Network Connection. The Will Message MUST be
        // published when the Network Connection is subsequently closed unless the Will Message has been deleted by the
        // Server on receipt of a DISCONNECT Packet.
        boolean willFlag = variableHeader.isWillFlag();
        if (willFlag) {
            MqttPublishMessage mqttPublishMessage = MqttMessageBuilders.publish()
                    .messageId(genMessageId(clientId))
                    .retained(variableHeader.isWillRetain())
                    .topicName(payload.willTopic())
                    .payload(Unpooled.buffer().writeBytes(payload.willMessageInBytes()))
                    .qos(MqttQoS.valueOf(variableHeader.willQos()))
                    .build();
            session.setWillMessage(mqttPublishMessage);
        }


    }

    @Override
    public MqttMessageType handleType() {
        return CONNECT;
    }

    /**
     * 生成一个唯一ID
     *
     * @return Unique Id
     */
    private String genClientId() {
        return NONE_ID_PREFIX + System.currentTimeMillis();
    }

    /**
     * todo 先以能用为主，后期在改吧
     *
     * @param clientId 客户端ID
     * @return messageId
     */
    private int genMessageId(String clientId) {
        return ThreadLocalRandom.current().nextInt(0, 65536);
    }
}
