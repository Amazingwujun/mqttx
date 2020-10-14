package com.jun.mqttx.broker.handler;

import com.jun.mqttx.entity.Session;
import com.jun.mqttx.service.IPubRelMessageService;
import com.jun.mqttx.service.IPublishMessageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;

/**
 * {@link MqttMessageType#PUBREC} 消息处理器
 *
 * @author Jun
 * @since 1.0.4
 */
@Handler(type = MqttMessageType.PUBREC)
public class PubRecHandler extends AbstractMqttSessionHandler {

    private IPubRelMessageService pubRelMessageService;
    private IPublishMessageService publishMessageService;

    public PubRecHandler(IPubRelMessageService pubRelMessageService, IPublishMessageService publishMessageService) {
        this.pubRelMessageService = pubRelMessageService;
        this.publishMessageService = publishMessageService;
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        // 移除消息
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = (MqttMessageIdVariableHeader) msg.variableHeader();
        int messageId = mqttMessageIdVariableHeader.messageId();
        if (clearSession(ctx)) {
            Session session = getSession(ctx);
            session.removePubMsg(messageId);
            session.savePubRelMsg(messageId);
        } else {
            String clientId = clientId(ctx);
            publishMessageService.remove(clientId, messageId);

            // 保存 pubRec
            pubRelMessageService.save(clientId, messageId);
        }

        MqttMessage mqttMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(messageId),
                null
        );
        ctx.writeAndFlush(mqttMessage);
    }
}