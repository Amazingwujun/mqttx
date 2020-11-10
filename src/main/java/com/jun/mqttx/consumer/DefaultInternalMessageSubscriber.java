package com.jun.mqttx.consumer;

import com.jun.mqttx.config.MqttxConfig;

import java.util.List;

/**
 * 集群消息订阅分发处理器, redis 实现
 *
 * @author Jun
 * @since 1.0.4
 */
public class DefaultInternalMessageSubscriber extends AbstractInnerChannel {

    public DefaultInternalMessageSubscriber(List<Watcher> watchers, MqttxConfig mqttxConfig) {
        super(watchers, mqttxConfig);
    }

    /**
     * 集群消息处理
     *
     * @param message 消息内容
     * @param channel 订阅频道
     */
    public void handleMessage(String message, String channel) {
        dispatch(message, channel);
    }
}