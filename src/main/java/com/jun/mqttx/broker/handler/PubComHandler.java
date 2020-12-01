package com.jun.mqttx.broker.handler;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.service.IPubRelMessageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;

/**
 * {@link MqttMessageType#PUBCOMP} 消息处理器
 *
 * @author Jun
 * @since 1.0.4
 */
@Handler(type = MqttMessageType.PUBCOMP)
public class PubComHandler extends AbstractMqttSessionHandler {

    private final IPubRelMessageService pubRelMessageService;

    public PubComHandler(IPubRelMessageService pubRelMessageService, MqttxConfig config) {
        super(config.getEnableTestMode(), config.getCluster().getEnable());
        this.pubRelMessageService = pubRelMessageService;
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = (MqttMessageIdVariableHeader) msg.variableHeader();
        int messageId = mqttMessageIdVariableHeader.messageId();
        if (isCleanSession(ctx)) {
            getSession(ctx).removePubRelOutMsg(messageId);
        } else {
            String clientId = clientId(ctx);
            pubRelMessageService.removeOut(clientId, messageId);
        }
    }

}