package com.jun.mqttx.broker.handler;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.service.IPubRelMessageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.springframework.util.Assert;

/**
 * {@link MqttMessageType#PUBREL} 消息处理器
 *
 * @author Jun
 * @since 1.0.4
 */
@Handler(type = MqttMessageType.PUBREL)
public class PubRelHandler extends AbstractMqttSessionHandler {

    private final IPubRelMessageService pubRelMessageService;

    public PubRelHandler(IPubRelMessageService pubRelMessageService, MqttxConfig config) {
        super(config.getEnableTestMode(), config.getCluster().getEnable());
        this.pubRelMessageService = pubRelMessageService;

        Assert.notNull(pubRelMessageService, "pubRelMessageService can't be null");
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = (MqttMessageIdVariableHeader) msg.variableHeader();
        int messageId = mqttMessageIdVariableHeader.messageId();
        if (isCleanSession(ctx)) {
            getSession(ctx).removePubRelMsg(messageId);
        } else {
            pubRelMessageService.removeIn(clientId(ctx), messageId);
        }

        MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId),
                null
        );
        ctx.writeAndFlush(mqttMessage);
    }
}