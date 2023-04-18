/*
 * Copyright 2020-2023 the original author or authors.
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
import com.jun.mqttx.entity.BrokerStatus;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.service.IRetainMessageService;
import com.jun.mqttx.service.ISubscriptionService;
import com.jun.mqttx.utils.TopicUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private final boolean enableTopicPubSubSecure;
    private final IRetainMessageService retainMessageService;
    private final ISubscriptionService subscriptionService;
    private final PublishHandler publishHandler;
    private final boolean enableSysTopic;
    private final String version;
    private final String brokerId;
    private long interval;
    /** 定时任务执行器 */
    private ScheduledExecutorService fixRateExecutor;

    //@formatter:on

    public SubscribeHandler(IRetainMessageService retainMessageService, ISubscriptionService subscriptionService,
                            PublishHandler publishHandler, MqttxConfig config) {
        super(config.getCluster().getEnable());
        this.retainMessageService = retainMessageService;
        this.publishHandler = publishHandler;
        this.subscriptionService = subscriptionService;
        this.enableTopicPubSubSecure = config.getEnableTopicSubPubSecure();

        this.version = config.getVersion();
        this.enableSysTopic = config.getSysTopic().getEnable();
        this.brokerId = config.getBrokerId();
        if (enableSysTopic) {
            this.interval = config.getSysTopic().getInterval().getSeconds();
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
        var needSave = new ArrayList<ClientSub>();
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
                        sysTopicSubscribeHandle(ClientSub.of(clientId, qos, topic, isCleanSession(ctx)), ctx);
                    } else {
                        // 区分开系统主题与普通主题
                        if (TopicUtils.isSys(topic)) {
                            qos = 0x80;
                        } else {
                            ClientSub clientSub = ClientSub.of(clientId, qos, topic, isCleanSession(ctx));
                            needSave.add(clientSub);
                        }
                    }
                }
            }
            grantedQosLevels.add(qos);
        });

        // 开始订阅
        Flux.fromIterable(needSave)
                .flatMap(subscriptionService::subscribe)
                .doOnComplete(() -> {
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
                        String topicFilter = mqttTopicSubscription.topicName();
                        retainMessageService.searchListByTopicFilter(topicFilter)
                                .flatMap(pubMsg -> {
                                    // When sending a PUBLISH Packet to a Client the Server MUST set the RETAIN flag to 1 if a
                                    // message is sent as a result of a new subscription being made by a Client [MQTT-3.3.1-8].
                                    pubMsg.setRetain(true);

                                    // 指定 clientId
                                    pubMsg.setAppointedClientId(clientId);
                                    return publishHandler.publish(pubMsg, ctx, false);
                                }).subscribe();
                    });
                }).subscribe();
    }

    /**
     * 系统 topic 任务处理，连接断开则订阅关系解除.
     * <h2>状态主题</h2>
     * <pre>
     * +-----------------------------------+----------------------------------------------------------------------------+
     * | 主题                              | 描述                                                                        |
     * +-----------------------------------+----------------------------------------------------------------------------+
     * | $SYS/broker/{brokerId}/status     | 触发方式：订阅此主题的客户端会定期（mqttx.sys-topic.interval）收到 broker 的状态，|
     * |                                   | 该状态涵盖下面所有主题的状态值.                                                |
     * |                                   | 注意：客户端连接断开后，订阅取消                                               |
     * +-----------------------------------+----------------------------------------------------------------------------+
     * | $SYS/broker/activeConnectCount    | 立即返回当前的活动连接数量 触发：订阅一次触发一次                                |
     * +-----------------------------------+----------------------------------------------------------------------------+
     * | $SYS/broker/time                  | 立即返回当前时间戳 触发：订阅一次触发一次                                       |
     * +-----------------------------------+----------------------------------------------------------------------------+
     * | $SYS/broker/version               | 立即返回 broker 版本 触发：订阅一次触发一次                                    |
     * +-----------------------------------+----------------------------------------------------------------------------+
     * | $SYS/broker/receivedMsg           | 立即返回 broker 启动到现在收到的 MqttMessage, 不含 ping 触发：订阅一次触发一次   |
     * +-----------------------------------+----------------------------------------------------------------------------+
     * | $SYS/broker/sendMsg               | 立即返回 broker 启动到现在发送的 MqttMessage, 不含 pingAck 触发：订阅一次触发一次|
     * +-----------------------------------+----------------------------------------------------------------------------+
     * | $SYS/broker/uptime                | 立即返回 broker 运行时长，单位秒 触发：订阅一次触发一次                          |
     * +-----------------------------------+----------------------------------------------------------------------------+
     * | $SYS/broker/maxActiveConnectCount | 立即返回 broker 运行至今的最大 tcp 连接数 触发：订阅一次触发一次                 |
     * +-----------------------------------+----------------------------------------------------------------------------+
     * </pre>
     * <h2>功能主题</h2>
     * <pre>
     * +------------------------------------------------------+---------------------------------------------------------+
     * | 主题                                                 | 描述                                                     |
     * +------------------------------------------------------+---------------------------------------------------------+
     * | $SYS/broker/{brokerId}/clients/{clientId}/connected  | 客户端成功连接至 broker  触发：当某个客户端上线后，          |
     * |                                                      | broker 会发送消息给该主题                                 |
     * +------------------------------------------------------+---------------------------------------------------------+
     * | $SYS/broker/{brokerId}/clients/{clientId}/disconnected | 触发：当某个客户端掉线后，broker 会发送消息给该主题        |
     * +------------------------------------------------------+---------------------------------------------------------+
     * </pre>
     *
     * @param clientSub 客户端订阅信息
     * @param ctx       {@link ChannelHandlerContext}
     */
    private void sysTopicSubscribeHandle(final ClientSub clientSub, final ChannelHandlerContext ctx) {
        final String topic = clientSub.getTopic();
        switch (topic) {
            case TopicUtils.BROKER_VERSION -> {
                byte[] version = BrokerStatus.builder()
                        .version(this.version)
                        .build().toJsonBytes();

                MqttPublishMessage versionResponse = MqttMessageBuilders.publish()
                        .qos(MqttQoS.AT_MOST_ONCE)
                        .retained(false)
                        .topicName(topic)
                        .payload(Unpooled.buffer(version.length).writeBytes(version))
                        .build();
                ctx.writeAndFlush(versionResponse);
            }
            case TopicUtils.BROKER_CLIENTS_ACTIVE_CONNECTED_COUNT -> {
                byte[] activeConnected = BrokerStatus.builder()
                        .activeConnectCount(BrokerHandler.CHANNELS.size())
                        .build().toJsonBytes();

                MqttPublishMessage timeResponse = MqttMessageBuilders.publish()
                        .qos(MqttQoS.AT_MOST_ONCE)
                        .retained(false)
                        .topicName(topic)
                        .payload(Unpooled.buffer(activeConnected.length).writeBytes(activeConnected))
                        .build();
                ctx.writeAndFlush(timeResponse);
            }
            case TopicUtils.BROKER_TIME -> {
                byte[] timestamp = BrokerStatus.builder()
                        .timestamp(LocalDateTime.now().toString())
                        .build().toJsonBytes();

                MqttPublishMessage timeResponse = MqttMessageBuilders.publish()
                        .qos(MqttQoS.AT_MOST_ONCE)
                        .retained(false)
                        .topicName(topic)
                        .payload(Unpooled.buffer(timestamp.length).writeBytes(timestamp))
                        .build();
                ctx.writeAndFlush(timeResponse);
            }
            case TopicUtils.BROKER_MAX_CLIENTS_ACTIVE -> {
                byte[] maxAlive = BrokerStatus.builder()
                        .maxActiveConnectCount(BrokerHandler.MAX_ACTIVE_SIZE.get())
                        .build().toJsonBytes();

                MqttPublishMessage timeResponse = MqttMessageBuilders.publish()
                        .qos(MqttQoS.AT_MOST_ONCE)
                        .retained(false)
                        .topicName(topic)
                        .payload(Unpooled.buffer(maxAlive.length).writeBytes(maxAlive))
                        .build();
                ctx.writeAndFlush(timeResponse);
            }
            case TopicUtils.BROKER_RECEIVED_MSG -> {
                byte[] received = BrokerStatus.builder()
                        .receivedMsg(ProbeHandler.IN_MSG_SIZE.intValue())
                        .build().toJsonBytes();

                MqttPublishMessage timeResponse = MqttMessageBuilders.publish()
                        .qos(MqttQoS.AT_MOST_ONCE)
                        .retained(false)
                        .topicName(topic)
                        .payload(Unpooled.buffer(received.length).writeBytes(received))
                        .build();
                ctx.writeAndFlush(timeResponse);
            }
            case TopicUtils.BROKER_SEND_MSG -> {
                byte[] send = BrokerStatus.builder()
                        .receivedMsg(ProbeHandler.OUT_MSG_SIZE.intValue())
                        .build().toJsonBytes();

                MqttPublishMessage timeResponse = MqttMessageBuilders.publish()
                        .qos(MqttQoS.AT_MOST_ONCE)
                        .retained(false)
                        .topicName(topic)
                        .payload(Unpooled.buffer(send.length).writeBytes(send))
                        .build();
                ctx.writeAndFlush(timeResponse);
            }
            case TopicUtils.BROKER_UPTIME -> {
                byte[] uptime = BrokerStatus
                        .builder()
                        .uptime((int) ((System.currentTimeMillis() - BrokerHandler.START_TIME) / 1000))
                        .build().toJsonBytes();

                MqttPublishMessage uptimeResponse = MqttMessageBuilders.publish()
                        .qos(MqttQoS.AT_MOST_ONCE)
                        .retained(false)
                        .topicName(topic)
                        .payload(Unpooled.buffer(uptime.length).writeBytes(uptime))
                        .build();
                ctx.writeAndFlush(uptimeResponse);
            }
            default -> {
                // 订阅功能主题
                subscriptionService.subscribeSys(clientSub).subscribe();
            }
        }
    }

    /**
     * 初始化系统定时主题发布任务，用于 {@link TopicUtils#BROKER_STATUS} 主题
     */
    private void initSystemStatePublishTimer() {
        fixRateExecutor.scheduleAtFixedRate(() -> {
            // 发布主题
            final String brokerStatusTopic = String.format(TopicUtils.BROKER_STATUS, brokerId);

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
                    .build().toJsonBytes();
            final var payload = Unpooled.wrappedBuffer(bytes);
            var mpm = MqttMessageBuilders.publish()
                    .qos(MqttQoS.AT_MOST_ONCE)
                    .retained(false)
                    .topicName(brokerStatusTopic)
                    .payload(payload)
                    .build();

            // 获取订阅客户端组
            subscriptionService.searchSysTopicClients(brokerStatusTopic)
                    .doOnNext(clientSub -> {
                        // 发布消息
                        Optional.ofNullable(ConnectHandler.CLIENT_MAP.get(clientSub.getClientId()))
                                .map(BrokerHandler.CHANNELS::find)
                                .ifPresent(channel -> channel.writeAndFlush(mpm.retain()));
                    })
                    .doOnComplete(mpm::release).subscribe();
        }, 0, interval, TimeUnit.SECONDS);
    }
}
