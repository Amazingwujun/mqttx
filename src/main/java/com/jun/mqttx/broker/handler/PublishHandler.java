package com.jun.mqttx.broker.handler;

import com.jun.mqttx.broker.BrokerHandler;
import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.common.constant.InternalMessageEnum;
import com.jun.mqttx.consumer.Watcher;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

/**
 * {@link MqttMessageType#PUBLISH} 处理器
 *
 * @author Jun
 * @date 2020-03-04 14:30
 */
@Component
public class PublishHandler extends AbstractMqttSessionHandler implements Watcher<PubMsg> {

    private IRetainMessageService retainMessageService;

    private ISubscriptionService subscriptionService;

    private IPublishMessageService publishMessageService;

    private IPubRelMessageService pubRelMessageService;

    private IInternalMessagePublishService internalMessagePublishService;

    private int brokerId;

    public PublishHandler(IPublishMessageService publishMessageService, IRetainMessageService retainMessageService,
                          ISubscriptionService subscriptionService, IPubRelMessageService pubRelMessageService,
                          IInternalMessagePublishService internalMessagePublishService, BizConfig bizConfig) {
        Assert.notNull(publishMessageService, "publishMessageService can't be null");
        Assert.notNull(retainMessageService, "retainMessageService can't be null");
        Assert.notNull(subscriptionService, "publishMessageService can't be null");
        Assert.notNull(pubRelMessageService, "publishMessageService can't be null");
        Assert.notNull(internalMessagePublishService, "internalMessagePublishService can't be null");
        Assert.notNull(bizConfig, "bizConfig can't be null");

        this.publishMessageService = publishMessageService;
        this.retainMessageService = retainMessageService;
        this.subscriptionService = subscriptionService;
        this.pubRelMessageService = pubRelMessageService;
        this.internalMessagePublishService = internalMessagePublishService;
        this.brokerId = bizConfig.getBrokerId();
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
        PubMsg pubMsg = new PubMsg(mqttQoS, packetId, topic, retain, data);

        //响应
        switch (mqttQoS) {
            case 0: //at most once
                publish(pubMsg, ctx, false);
                break;
            case 1: //at least once
                publish(pubMsg, ctx, false);
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
                    publish(pubMsg, ctx, false);
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
    public void action(InternalMessage<PubMsg> im) {
        PubMsg data = im.getData();
        publish(data, null, true);
    }

    @Override
    public MqttMessageType handleType() {
        return MqttMessageType.PUBLISH;
    }

    @Override
    public String channel() {
        return InternalMessageEnum.PUB.getChannel();
    }

    /**
     * 消息发布
     *
     * @param pubMsg            publish message
     * @param isInternalMessage 标志消息源是集群还是客户端
     */
    private void publish(final PubMsg pubMsg, ChannelHandlerContext ctx, boolean isInternalMessage) {
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
            int messageId;
            if (isInternalMessage) {
                messageId = pubMsg.getMessageId();
            } else {
                messageId = nextMessageId(ctx);
                pubMsg.setMessageId(messageId);
            }
            MqttPublishMessage mpm = MqttMessageBuilders.publish()
                    .messageId(messageId)
                    .qos(qos)
                    .topicName(topic)
                    .retained(pubMsg.isRetain())
                    .payload(Unpooled.wrappedBuffer(pubMsg.getPayload()))
                    .build();

            //集群消息不做保存，因为传播消息的 broker 已经保存过了
            if ((qos == MqttQoS.EXACTLY_ONCE || qos == MqttQoS.AT_LEAST_ONCE) && !isInternalMessage) {
                publishMessageService.save(clientId, pubMsg);
            }

            //将消息推送给集群中的broker
            internalMessagePublish(pubMsg);

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

    /**
     * 集群内部消息发布
     *
     * @param pubMsg {@link PubMsg}
     */
    private void internalMessagePublish(PubMsg pubMsg) {
        InternalMessage<PubMsg> im = new InternalMessage<>(pubMsg, System.currentTimeMillis(), brokerId);
        internalMessagePublishService.publish(im, channel());
    }
}
