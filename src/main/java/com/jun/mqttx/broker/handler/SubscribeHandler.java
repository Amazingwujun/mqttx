package com.jun.mqttx.broker.handler;

import com.jun.mqttx.broker.BrokerHandler;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.BrokerStatus;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IRetainMessageService;
import com.jun.mqttx.service.ISubscriptionService;
import com.jun.mqttx.utils.TopicUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.mqtt.*;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * {@link MqttMessageType#SUBSCRIBE} 消息处理器
 *
 * @author Jun
 * @since 1.0.4
 */
@Handler(type = MqttMessageType.SUBSCRIBE)
public class SubscribeHandler extends AbstractMqttTopicSecureHandler {
    //@formatter:off

    /**系统主题 $SYS 订阅群组 */
    static ChannelGroup SYS_CHANNELS;
    private final boolean enableTopicPubSubSecure;
    private final IRetainMessageService retainMessageService;
    private final ISubscriptionService subscriptionService;
    private final boolean enableSysTopic;
    private long interval;
    private MqttQoS sysTopicQos;
    private final String version;
    /** 定时任务执行器 */
    private ScheduledExecutorService fixRateExecutor;

    //@formatter:on

    public SubscribeHandler(IRetainMessageService retainMessageService, ISubscriptionService subscriptionService,
                            MqttxConfig mqttxConfig) {
        this.retainMessageService = retainMessageService;
        this.subscriptionService = subscriptionService;
        this.enableTopicPubSubSecure = mqttxConfig.getEnableTopicSubPubSecure();

        this.version = mqttxConfig.getVersion();
        this.enableSysTopic = mqttxConfig.getSysTopic().getEnable();
        if (enableSysTopic) {
            SubscribeHandler.SYS_CHANNELS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
            this.interval = mqttxConfig.getSysTopic().getInterval().getSeconds();
            this.sysTopicQos = MqttQoS.valueOf(mqttxConfig.getSysTopic().getQos());
            fixRateExecutor = Executors.newSingleThreadScheduledExecutor();
            initSystemStatePublishTimer();
        }
    }

    /**
     * 订阅消息处理
     * <p>
     *
     * @param ctx 见 {@link ChannelHandlerContext}
     * @param msg 解包后的数据
     */
    @Override
    public void process(final ChannelHandlerContext ctx, MqttMessage msg) {
        // 获取订阅的topic、clientId
        MqttSubscribeMessage mqttSubscribeMessage = (MqttSubscribeMessage) msg;
        int messageId = mqttSubscribeMessage.variableHeader().messageId();
        List<MqttTopicSubscription> mqttTopicSubscriptions = mqttSubscribeMessage.payload().topicSubscriptions();
        String clientId = clientId(ctx);

        // 保存用户订阅
        // 考虑到某些 topic 的订阅可能不开放给某些 client，针对这些 topic，我们有必要增加权限校验。实现办法有很多，目前的校验机制：
        // 当 client 连接并调用认证服务时，认证服务返回 client 具备的哪些 topic 订阅权限，当 enableTopicSubscribeSecure = true 时，
        // 程序将校验 client 当前想要订阅的 topic 是否被授权
        List<Integer> grantedQosLevels = new ArrayList<>(mqttTopicSubscriptions.size());
        mqttTopicSubscriptions.forEach(mqttTopicSubscription -> {
            final String topic = mqttTopicSubscription.topicName();
            int qos = mqttTopicSubscription.qualityOfService().value();

            if (!TopicUtils.isValid(topic)) {
                // Failure
                qos = 0x80;
            } else {
                if (enableTopicPubSubSecure && !hasAuthToSubTopic(ctx, topic)) {
                    // client 不允许订阅此 topic
                    qos = 0x80;
                } else {
                    // 系统主题消息订阅, 则定时发布订阅的主题给客户端
                    // 系统 topic 订阅结果不存储
                    if (enableSysTopic && TopicUtils.isSys(topic)) {
                        sysTopicSubscribeHandle(topic, ctx);
                    } else {
                        ClientSub clientSub = new ClientSub(clientId, qos, topic);
                        subscriptionService.subscribe(clientSub);
                    }
                }
            }
            grantedQosLevels.add(qos);
        });

        // acknowledge
        MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId),
                new MqttSubAckPayload(grantedQosLevels));
        ctx.writeAndFlush(mqttMessage);

        // When a new subscription is established, the last retained message, if any,
        // on each matching topic name MUST be sent to the subscriber [MQTT-3.3.1-6]
        // 获取所有存在保留消息的 topic, 当 TopicUtils.match(topic, newSubTopic) = true 时，将保留消息发送给客户端
        mqttTopicSubscriptions.forEach(mqttTopicSubscription -> {
            String newSubTopic = mqttTopicSubscription.topicName();
            for (PubMsg pubMsg : retainMessageService.searchListBySubTopic(newSubTopic)) {
                MqttPublishMessage mpm = MqttMessageBuilders.publish()
                        .qos(MqttQoS.valueOf(pubMsg.getQoS()))
                        .retained(true)
                        .topicName(newSubTopic)
                        .messageId(nextMessageId(ctx))
                        .payload(Unpooled.wrappedBuffer(pubMsg.getPayload()))
                        .build();

                ctx.writeAndFlush(mpm);
            }
        });
    }

    /**
     * 系统 topic 任务处理. 定时发送的系统主题（repeat = true）订阅状态与 tcp 连接状态相关，连接断开则订阅关系解除.
     * 系统主题:
     * <pre>
     * +-----------------------------------+--------+--------------------------------------------------------------------------+
     * | topic                             | repeat | comment                                                                  |
     * +-----------------------------------+--------+--------------------------------------------------------------------------+
     * | $SYS/broker/status                | false  | 订阅此主题的客户端会定期（mqttx.sys-topic.interval）收到 broker 的状态， |
     * |                                   |        | 该状态涵盖下面所有主题的状态值。                                         |
     * |                                   |        | 注意：客户端连接断开后，订阅取消                                         |
     * +-----------------------------------+--------+--------------------------------------------------------------------------+
     * | $SYS/broker/activeConnectCount    | true   | 立即返回当前的活动连接数量                                               |
     * +-----------------------------------+--------+--------------------------------------------------------------------------+
     * | $SYS/broker/time                  | true   | 立即返回当前时间戳                                                       |
     * +-----------------------------------+--------+--------------------------------------------------------------------------+
     * | $SYS/broker/version               | true   | 立即返回 broker 版本                                                     |
     * +-----------------------------------+--------+--------------------------------------------------------------------------+
     * | $SYS/broker/receivedMsg           | true   | 立即返回 broker 启动到现在收到的 MqttMessage, 不含 ping                  |
     * +-----------------------------------+--------+--------------------------------------------------------------------------+
     * | $SYS/broker/sendMsg               | true   | 立即返回 broker 启动到现在发送的 MqttMessage, 不含 pingAck               |
     * +-----------------------------------+--------+--------------------------------------------------------------------------+
     * | $SYS/broker/uptime                | true   | 立即返回 broker 运行时长，单位秒                                         |
     * +-----------------------------------+--------+--------------------------------------------------------------------------+
     * | $SYS/broker/maxActiveConnectCount | true   | 立即返回 broker 运行至今的最大 tcp 连接数                                |
     * +-----------------------------------+--------+--------------------------------------------------------------------------+
     * </pre>
     * repeat 说明:
     * <ul>
     *     <li>当 repeat = false : 只需订阅一次，broker 会定时发布数据到此主题.</li>
     *     <li>当 repeat = true : 订阅一次，发布一次.</li>
     * </ul>
     *
     * @param topic 系统主题
     * @param ctx   {@link ChannelHandlerContext}
     */
    private void sysTopicSubscribeHandle(final String topic, final ChannelHandlerContext ctx) {
        final int messageId = nextMessageId(ctx);
        switch (topic) {
            case TopicUtils.BROKER_STATUS: {
                SYS_CHANNELS.add(ctx.channel());
                break;
            }
            case TopicUtils.BROKER_VERSION: {
                byte[] version = BrokerStatus.builder()
                        .version(this.version)
                        .build().toUtf8Bytes();

                MqttPublishMessage versionResponse = MqttMessageBuilders.publish()
                        .qos(sysTopicQos)
                        .retained(false)
                        .topicName(topic)
                        .messageId(messageId)
                        .payload(Unpooled.buffer(version.length).writeBytes(version))
                        .build();
                ctx.writeAndFlush(versionResponse);
                break;
            }
            case TopicUtils.BROKER_CLIENTS_ACTIVE_CONNECTED_COUNT: {
                byte[] activeConnected = BrokerStatus.builder()
                        .activeConnectCount(BrokerHandler.CHANNELS.size())
                        .build().toUtf8Bytes();

                MqttPublishMessage timeResponse = MqttMessageBuilders.publish()
                        .qos(sysTopicQos)
                        .retained(false)
                        .topicName(topic)
                        .messageId(messageId)
                        .payload(Unpooled.buffer(activeConnected.length).writeBytes(activeConnected))
                        .build();
                ctx.writeAndFlush(timeResponse);
                break;
            }
            case TopicUtils.BROKER_TIME: {
                byte[] timestamp = BrokerStatus.builder()
                        .timestamp(LocalDateTime.now().toString())
                        .build().toUtf8Bytes();

                MqttPublishMessage timeResponse = MqttMessageBuilders.publish()
                        .qos(sysTopicQos)
                        .retained(false)
                        .topicName(topic)
                        .messageId(messageId)
                        .payload(Unpooled.buffer(timestamp.length).writeBytes(timestamp))
                        .build();
                ctx.writeAndFlush(timeResponse);
                break;
            }
            case TopicUtils.BROKER_MAX_CLIENTS_ACTIVE: {
                byte[] maxAlive = BrokerStatus.builder()
                        .maxActiveConnectCount(BrokerHandler.MAX_ACTIVE_SIZE.get())
                        .build().toUtf8Bytes();

                MqttPublishMessage timeResponse = MqttMessageBuilders.publish()
                        .qos(sysTopicQos)
                        .retained(false)
                        .topicName(topic)
                        .messageId(messageId)
                        .payload(Unpooled.buffer(maxAlive.length).writeBytes(maxAlive))
                        .build();
                ctx.writeAndFlush(timeResponse);
                break;
            }
            case TopicUtils.BROKER_RECEIVED_MSG:{
                byte[] received = BrokerStatus.builder()
                        .receivedMsg(ProbeHandler.IN_MSG_SIZE.intValue())
                        .build().toUtf8Bytes();

                MqttPublishMessage timeResponse = MqttMessageBuilders.publish()
                        .qos(sysTopicQos)
                        .retained(false)
                        .topicName(topic)
                        .messageId(messageId)
                        .payload(Unpooled.buffer(received.length).writeBytes(received))
                        .build();
                ctx.writeAndFlush(timeResponse);

                break;
            }
            case TopicUtils.BROKER_SEND_MSG:{
                byte[] send = BrokerStatus.builder()
                        .receivedMsg(ProbeHandler.OUT_MSG_SIZE.intValue())
                        .build().toUtf8Bytes();

                MqttPublishMessage timeResponse = MqttMessageBuilders.publish()
                        .qos(sysTopicQos)
                        .retained(false)
                        .topicName(topic)
                        .messageId(messageId)
                        .payload(Unpooled.buffer(send.length).writeBytes(send))
                        .build();
                ctx.writeAndFlush(timeResponse);

                break;
            }
            case TopicUtils.BROKER_UPTIME: {
                byte[] uptime = BrokerStatus
                        .builder()
                        .uptime((int) ((System.currentTimeMillis() - BrokerHandler.START_TIME) / 1000))
                        .build().toUtf8Bytes();

                MqttPublishMessage uptimeResponse = MqttMessageBuilders.publish()
                        .qos(sysTopicQos)
                        .retained(false)
                        .topicName(topic)
                        .messageId(messageId)
                        .payload(Unpooled.buffer(uptime.length).writeBytes(uptime))
                        .build();
                ctx.writeAndFlush(uptimeResponse);
                break;
            }
            default:
                // unreachable code
        }
    }

    /**
     * 初始化系统定时主题发布任务，用于 {@link TopicUtils#BROKER_STATUS} 主题
     */
    private void initSystemStatePublishTimer() {
        fixRateExecutor.scheduleAtFixedRate(() -> {
            if (SYS_CHANNELS.size() == 0) {
                return;
            }

            // broker 状态
            LocalDateTime now = LocalDateTime.now();
            byte[] bytes = BrokerStatus.builder()
                    .activeConnectCount(BrokerHandler.CHANNELS.size())
                    .maxActiveConnectCount(BrokerHandler.MAX_ACTIVE_SIZE.get())
                    .receivedMsg(ProbeHandler.IN_MSG_SIZE.intValue())
                    .sendMsg(ProbeHandler.OUT_MSG_SIZE.intValue())
                    .timestamp(now.toString())
                    .uptime((int) ((System.currentTimeMillis() - BrokerHandler.START_TIME) / 1000))
                    .version(this.version)
                    .build().toUtf8Bytes();
            ByteBuf payload = Unpooled.buffer(bytes.length).writeBytes(bytes);

            // 遍历发送
            SYS_CHANNELS.forEach(channel -> {
                MqttPublishMessage mpm = MqttMessageBuilders.publish()
                        .qos(sysTopicQos)
                        .retained(false)
                        .topicName(TopicUtils.BROKER_STATUS)
                        .messageId(nextMessageId(channel))
                        .payload(payload)
                        .build();
                channel.writeAndFlush(mpm);
            });
        }, 0, interval, TimeUnit.SECONDS);
    }
}