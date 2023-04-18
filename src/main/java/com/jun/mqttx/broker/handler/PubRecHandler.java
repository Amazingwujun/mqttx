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

import com.jun.mqttx.config.MqttxConfig;
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

    private final IPubRelMessageService pubRelMessageService;
    private final IPublishMessageService publishMessageService;

    public PubRecHandler(IPubRelMessageService pubRelMessageService, IPublishMessageService publishMessageService,
                         MqttxConfig config) {
        super(config.getCluster().getEnable());
        this.pubRelMessageService = pubRelMessageService;
        this.publishMessageService = publishMessageService;
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        // 移除消息
        final var mqttMessageIdVariableHeader = (MqttMessageIdVariableHeader) msg.variableHeader();
        int messageId = mqttMessageIdVariableHeader.messageId();
        if (isCleanSession(ctx)) {
            Session session = getSession(ctx);
            session.removePubMsg(messageId);
            session.savePubRelOutMsg(messageId);

            final var mqttMessage = MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                    MqttMessageIdVariableHeader.from(messageId),
                    null
            );
            ctx.writeAndFlush(mqttMessage);
        } else {
            String clientId = clientId(ctx);
            publishMessageService.remove(clientId, messageId)
                    .then(pubRelMessageService.saveOut(clientId, messageId))
                    .doOnSuccess(unused -> {
                        final var mqttMessage = MqttMessageFactory.newMessage(
                                new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0),
                                MqttMessageIdVariableHeader.from(messageId),
                                null
                        );
                        ctx.writeAndFlush(mqttMessage);
                    })
                    .subscribe();
        }
    }
}
