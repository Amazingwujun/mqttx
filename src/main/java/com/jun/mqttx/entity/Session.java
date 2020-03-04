package com.jun.mqttx.entity;

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
    private MqttPublishMessage willMessage;
}
