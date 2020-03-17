package com.jun.mqttx.broker.handler;

import com.jun.mqttx.broker.BrokerHandler;
import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IPubRelMessageService;
import com.jun.mqttx.service.IPublishMessageService;
import com.jun.mqttx.service.IRetainMessageService;
import com.jun.mqttx.service.ISubscriptionService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@link MqttMessageType#PUBLISH} 处理器
 *
 * @author Jun
 * @date 2020-03-04 14:30
 */
@Component
public class PublishHandler extends AbstractMqttMessageHandler {

    private IRetainMessageService retainMessageService;

    private ISubscriptionService subscriptionService;

    private IPublishMessageService publishMessageService;

    private IPubRelMessageService pubRelMessageService;

    public PublishHandler(StringRedisTemplate stringRedisTemplate, BizConfig bizConfig, IPublishMessageService publishMessageService,
                          IRetainMessageService retainMessageService, ISubscriptionService subscriptionService, IPubRelMessageService pubRelMessageService) {
        super(stringRedisTemplate, bizConfig);

        this.publishMessageService = publishMessageService;
        this.retainMessageService = retainMessageService;
        this.subscriptionService = subscriptionService;
        this.pubRelMessageService = pubRelMessageService;
    }

    /**
     * 根据 MQTT v3.1.1 Qos2 实现有 Method A 与 Method B,这里采用 B 方案，
     * 具体参见 <b>Figure 4.3-Qos protocol flow diagram,non normative example</b>
     *
     * @param ctx 见 {@link ChannelHandlerContext}
     * @param msg 解包后的数据
     */
    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttPublishMessage mpm = (MqttPublishMessage) msg;
        MqttFixedHeader mqttFixedHeader = mpm.fixedHeader();
        MqttPublishVariableHeader mqttPublishVariableHeader = mpm.variableHeader();
        ByteBuf payload = mpm.payload();

        //获取qos、topic、packetId、retain、payload
        int mqttQoS = mqttFixedHeader.qosLevel().value();
        String topic = mqttPublishVariableHeader.topicName();
        int packetId = mqttPublishVariableHeader.packetId();
        boolean retain = mqttFixedHeader.isRetain();
        byte[] data = new byte[payload.readableBytes()];
        payload.readBytes(data);

        //组装消息
        //todo 后期实现集群时，这里需要加入内部消息通知机制
        PubMsg pubMsg = new PubMsg(mqttQoS, packetId, topic, retain, data);

        //响应
        switch (mqttQoS) {
            case 0: //at most once
                publish(pubMsg);
                break;
            case 1: //at least once
                publish(pubMsg);
                MqttMessage pubAck = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.valueOf(mqttQoS), retain, 0),
                        MqttMessageIdVariableHeader.from(packetId),
                        null
                );
                ctx.writeAndFlush(pubAck);
                break;
            case 2: //exactly once
                MqttMessage pubRec = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.valueOf(mqttQoS), retain, 0),
                        MqttMessageIdVariableHeader.from(packetId),
                        null
                );
                ctx.writeAndFlush(pubRec);

                //判断消息是否重复
                if (!pubRelMessageService.isDupMsg(clientId(ctx), packetId)) {
                    //发布新的消息并保存 pubRel 标记，用于实现Qos2
                    publish(pubMsg);
                    pubRelMessageService.save(clientId(ctx), packetId);
                }
                break;
        }

        //retain 消息处理
        if (mqttFixedHeader.isRetain()) {
            handleRetainMsg(pubMsg);
        }
    }

    @Override
    public MqttMessageType handleType() {
        return MqttMessageType.PUBLISH;
    }

    /**
     * publish message to client
     *
     * @param pubMsg publish message
     */
    private void publish(PubMsg pubMsg) {
        //获取topic订阅者id列表
        String topic = pubMsg.getTopic();
        List<ClientSub> clientList = subscriptionService.searchSubscribeClientList(topic);

        //遍历发送
        clientList.forEach(clientSub -> {
            final String clientId = clientSub.getClientId();

            //计算Qos
            int pubQos = pubMsg.getQoS();
            int subQos = clientSub.getQos();
            MqttQoS qos = subQos >= pubQos ? MqttQoS.valueOf(pubQos) : MqttQoS.valueOf(subQos);

            //组装PubMsg
            MqttPublishMessage mpm = MqttMessageBuilders.publish()
                    .messageId(nextMessageId(clientId))
                    .qos(qos)
                    .topicName(topic)
                    .retained(pubMsg.isRetain())
                    .payload(Unpooled.wrappedBuffer(pubMsg.getPayload()))
                    .build();
            if (qos == MqttQoS.EXACTLY_ONCE || qos == MqttQoS.AT_LEAST_ONCE) {
                publishMessageService.save(clientId, pubMsg);
            }

            //发送
            Optional.of(clientId)
                    .map(ConnectHandler.clientMap::get)
                    .map(BrokerHandler.channels::find)
                    .ifPresent(channel -> channel.writeAndFlush(mpm));
        });
    }

    /**
     * 处理 retain 消息
     *
     * @param pubMsg retain message
     */
    private void handleRetainMsg(PubMsg pubMsg) {
        byte[] payload = pubMsg.getPayload();
        String topic = pubMsg.getTopic();
        int qos = pubMsg.getQoS();

        //如果 retain = 1 且 payload bytes.size = 0
        if (payload == null || payload.length == 0) {
            subscriptionService.removeTopic(topic);
            return;
        }

        //如果 qos = 0 且  retain = 1
        if (MqttQoS.AT_MOST_ONCE.value() == qos) {
            retainMessageService.remove(topic);
            return;
        }

        retainMessageService.save(topic, pubMsg);
    }
}
