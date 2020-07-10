package com.jun.mqttx.broker.handler;

import io.netty.handler.codec.mqtt.MqttMessageType;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * mqtt 消息处理器
 *
 * @author Jun
 * @date 2020-07-10 17:15
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Handler {

    /**
     * 处理器支持的消息类别
     */
    MqttMessageType type();
}
