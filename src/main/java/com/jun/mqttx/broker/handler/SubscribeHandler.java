package com.jun.mqttx.broker.handler;

import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.service.IRetainMessageService;
import com.jun.mqttx.service.ISubscriptionService;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link MqttMessageType#SUBSCRIBE} 消息处理器
 *
 * @author Jun
 * @date 2020-03-04 16:05
 */
@Component
public class SubscribeHandler extends AbstractMqttSessionHandler {

    private IRetainMessageService retainMessageService;

    private ISubscriptionService subscriptionService;

    public SubscribeHandler(IRetainMessageService retainMessageService, ISubscriptionService subscriptionService) {
        this.retainMessageService = retainMessageService;
        this.subscriptionService = subscriptionService;
    }


    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        //获取订阅的topic、clientId
        MqttSubscribeMessage mqttSubscribeMessage = (MqttSubscribeMessage) msg;
        int messageId = mqttSubscribeMessage.variableHeader().messageId();
        List<MqttTopicSubscription> mqttTopicSubscriptions = mqttSubscribeMessage.payload().topicSubscriptions();
        String clientId = clientId(ctx);

        //保存用户订阅
        List<Integer> grantedQosLevels = new ArrayList<>(mqttTopicSubscriptions.size());
        mqttTopicSubscriptions.forEach(mqttTopicSubscription -> {
            String topic = mqttTopicSubscription.topicName();
            int qos = mqttTopicSubscription.qualityOfService().value();

            if (!isValidTopic(topic)) {
                //Failure
                qos = 0x80;
            } else {
                ClientSub clientSub = new ClientSub(clientId, qos, topic);
                subscriptionService.subscribe(clientSub);
            }
            grantedQosLevels.add(qos);
        });

        //acknowledge
        MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId),
                new MqttSubAckPayload(grantedQosLevels));
        ctx.writeAndFlush(mqttMessage);

        //publish retain message with new subscribe
        mqttTopicSubscriptions.forEach(mqttTopicSubscription -> {
            String topic = mqttTopicSubscription.topicName();
            PubMsg pubMsg = retainMessageService.get(topic);

            if (pubMsg != null) {
                MqttPublishMessage mpm = MqttMessageBuilders.publish()
                        .qos(MqttQoS.valueOf(pubMsg.getQoS()))
                        .retained(true)
                        .topicName(topic)
                        .messageId(nextMessageId(ctx))
                        .payload(Unpooled.wrappedBuffer(pubMsg.getPayload()))
                        .build();

                ctx.writeAndFlush(mpm);
            }
        });
    }

    @Override
    public MqttMessageType handleType() {
        return MqttMessageType.SUBSCRIBE;
    }

    /**
     * 用于判定 topic 是否合法，目前 mqttx 尚不支持通配符 <b>+,*</b>
     *
     * @param topic 主题
     * @return true if topic valid
     */
    private boolean isValidTopic(String topic) {
        if (StringUtils.isEmpty(topic)) {
            return false;
        }

        if (topic.contains("*") || topic.contains("+") ||
                topic.endsWith("/") || topic.contains("#")) {
            return false;
        }

        return true;
    }
}
