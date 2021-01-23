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

package com.jun.mqttx.consumer;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.utils.Serializer;

import java.util.List;

/**
 * 集群消息订阅分发处理器, redis 实现
 *
 * @author Jun
 * @since 1.0.4
 */
public class DefaultInternalMessageSubscriber extends AbstractInnerChannel {

    public DefaultInternalMessageSubscriber(List<Watcher> watchers, Serializer serializer, MqttxConfig mqttxConfig) {
        super(watchers, serializer, mqttxConfig);
    }

    /**
     * 集群消息处理
     *
     * @param message 消息内容
     * @param channel 订阅频道
     */
    public void handleMessage(byte[] message, String channel) {
        dispatch(message, channel);
    }
}