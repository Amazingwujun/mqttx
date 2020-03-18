package com.jun.mqttx.entity;

import com.alibaba.fastjson.annotation.JSONField;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.Data;

/**
 * MQTT 会话
 *
 * @author Jun
 * @date 2020-03-04 13:19
 */
@Data
public class Session {

    /**
     * 客户ID
     */
    private String clientId;

    /**
     * 清理会话标志
     */
    private Boolean clearSession;

    /**
     * 遗嘱消息
     */
    @JSONField(serialize = false, deserialize = false)
    private MqttPublishMessage willMessage;

    /**
     * 用于生成 msgId
     */
    private int messageId;

    /**
     * session 绑定 channel, 而channel 是绑定了 EventLoop 线程的，这个方法是线程安全的（如果没有额外的配置）。
     *
     * @return {@link #messageId}
     */
    public int increaseAndGetMessageId() {
        return ++messageId;
    }
}
