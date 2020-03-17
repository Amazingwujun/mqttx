package com.jun.mqttx.entity;

import com.alibaba.fastjson.annotation.JSONField;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

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
}
