/*
 * Copyright 2002-2020 the original author or authors.
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

import com.alibaba.fastjson.TypeReference;
import com.jun.mqttx.broker.BrokerHandler;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.constants.InternalMessageEnum;
import com.jun.mqttx.consumer.Watcher;
import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.entity.Session;
import com.jun.mqttx.utils.JsonSerializer;
import com.jun.mqttx.utils.Serializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * {@link MqttMessageType#DISCONNECT} 消息处理器
 *
 * @author Jun
 * @since 1.0.4
 */
@Slf4j
@Handler(type = MqttMessageType.DISCONNECT)
public final class DisconnectHandler extends AbstractMqttSessionHandler implements Watcher {

    private final Serializer serializer;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public DisconnectHandler(Serializer serializer, MqttxConfig config) {
        super(config.getCluster().getEnable());
        this.serializer = serializer;
    }

    /**
     * 处理 disconnect 报文
     *
     * @param ctx 见 {@link ChannelHandlerContext}
     * @param msg 解包后的数据
     */
    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage msg) {
        // [MQTT-3.1.2-8]
        // If the Will Flag is set to 1 this indicates that, if the Connect request is accepted, a Will Message MUST be
        // stored on the Server and associated with the Network Connection. The Will Message MUST be published when the
        // Network Connection is subsequently closed unless the Will Message has been deleted by the Server on receipt of
        // a DISCONNECT Packet.
        Session session = getSession(ctx);
        session.clearWillMessage();

        ctx.close();
    }

    @Override
    public void action(byte[] msg) {
        InternalMessage<String> im;
        if (serializer instanceof JsonSerializer) {
            im = ((JsonSerializer) serializer).deserialize(msg, new TypeReference<InternalMessage<String>>() {
            });
        } else {
            //noinspection unchecked
            im = serializer.deserialize(msg, InternalMessage.class);
        }

        Optional.ofNullable(im.getData())
                .map(ConnectHandler.CLIENT_MAP::get)
                .map(BrokerHandler.CHANNELS::find)
                .map(ChannelOutboundInvoker::close);
    }

    @Override
    public boolean support(String channel) {
        return InternalMessageEnum.DISCONNECT.getChannel().equals(channel);
    }
}
