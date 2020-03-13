package com.jun.mqttx.server.handler;

import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.server.BrokerHandler;
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

    public PublishHandler(StringRedisTemplate stringRedisTemplate, BizConfig bizConfig, IPublishMessageService publishMessageService,
                          IRetainMessageService retainMessageService, ISubscriptionService subscriptionService) {
        super(stringRedisTemplate, bizConfig);

        this.publishMessageService = publishMessageService;
        this.retainMessageService = retainMessageService;
        this.subscriptionService = subscriptionService;
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttPublishMessage mpm = (MqttPublishMessage) msg;
        MqttFixedHeader mqttFixedHeader = mpm.fixedHeader();
        MqttPublishVariableHeader mqttPublishVariableHeader = mpm.variableHeader();
        ByteBuf payload = mpm.payload();

        //获取qos、topic、packetId、retain、payload
        MqttQoS mqttQoS = mqttFixedHeader.qosLevel();
        String topic = mqttPublishVariableHeader.topicName();
        int packetId = mqttPublishVariableHeader.packetId();
        boolean retain = mqttFixedHeader.isRetain();
        byte[] data = new byte[payload.readableBytes()];
        payload.writeBytes(data);

        //发布消息
        PubMsg pubMsg = new PubMsg(mqttQoS.value(), packetId, topic, retain, data);
        publish(pubMsg);

        //retain 消息处理
        if (mqttFixedHeader.isRetain()) {
            //如果 retain = 1 且 payload bytes.size = 0
            if (payload.isReadable()) {
                subscriptionService.removeTopic(topic);
                return;
            }

            //如果 qos = 0 且  retain = 1
            if (MqttQoS.AT_LEAST_ONCE == mqttQoS) {
                retainMessageService.remove(topic);
                return;
            }

            retainMessageService.save(topic, pubMsg);
        }
    }

    @Override
    public MqttMessageType handleType() {
        return MqttMessageType.PUBLISH;
    }

    /**
     * publish message to client
     * //todo 当前不支持集群，所以没有内部消息通知机制
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
                    .payload(Unpooled.copiedBuffer(pubMsg.getPayload()))
                    .build();
            if (qos == MqttQoS.EXACTLY_ONCE || qos == MqttQoS.AT_LEAST_ONCE) {
                publishMessageService.save(pubMsg);
            }

            //发送
            Optional.of(clientId)
                    .map(ConnectHandler.clientMap::get)
                    .map(BrokerHandler.channels::find)
                    .ifPresent(channel -> channel.writeAndFlush(mpm));
        });
    }
}
