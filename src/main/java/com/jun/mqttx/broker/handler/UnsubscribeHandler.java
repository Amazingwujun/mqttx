package com.jun.mqttx.broker.handler;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.service.ISubscriptionService;
import com.jun.mqttx.utils.TopicUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link MqttMessageType#UNSUBSCRIBE} 消息处理器
 *
 * @author Jun
 * @since 1.0.4
 */
@Handler(type = MqttMessageType.UNSUBSCRIBE)
public class UnsubscribeHandler extends AbstractMqttSessionHandler {

    private Boolean enableSysTopic;

    private ISubscriptionService subscriptionService;

    public UnsubscribeHandler(MqttxConfig config, ISubscriptionService subscriptionService) {
        this.enableSysTopic = config.getSysTopic().getEnable();
        this.subscriptionService = subscriptionService;
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttUnsubscribeMessage mqttUnsubscribeMessage = (MqttUnsubscribeMessage) msg;
        int messageId = mqttUnsubscribeMessage.variableHeader().messageId();
        MqttUnsubscribePayload payload = mqttUnsubscribeMessage.payload();

        // 系统主题
        List<String> collect = payload.topics();
        if (enableSysTopic) {
            collect = unsubscribeSysTopics(payload.topics(), ctx.channel());
        }

        // 非系统主题
        subscriptionService.unsubscribe(clientId(ctx), collect);

        // response
        MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId),
                null
        );
        ctx.writeAndFlush(mqttMessage);
    }

    /**
     * 系统主题订阅处理. 系统主题订阅没有持久化，仅保存在内存，需要单独处理.
     *
     * @param unSub   解除订阅的主题列表
     * @param channel {@link Channel}
     * @return 非系统主题列表
     */
    private List<String> unsubscribeSysTopics(List<String> unSub, Channel channel) {
        return unSub.stream()
                .peek(topic -> {
                    if (TopicUtils.BROKER_STATUS.equals(topic)) {
                        SubscribeHandler.SYS_CHANNELS.remove(channel);
                    }
                })
                .filter(topic -> !TopicUtils.isSys(topic))
                .collect(Collectors.toList());

    }
}