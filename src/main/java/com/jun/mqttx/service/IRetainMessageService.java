package com.jun.mqttx.service;

import com.jun.mqttx.entity.PubMsg;

/**
 * retain 消息服务
 *
 * @author Jun
 * @date 2020-03-09 20:17
 */
public interface IRetainMessageService {

    /**
     * 存储当前 topic 的 retain 消息
     *
     * @param topic  主题
     * @param pubMsg 发布消息
     */
    void save(String topic, PubMsg pubMsg);

    /**
     * 移除 topic 的 retain 消息
     *
     * @param topic 主题
     */
    void remove(String topic);

    /**
     * 获取订阅主题的保留信息
     *
     * @param topic 主题
     * @return {@link PubMsg}
     */
    PubMsg get(String topic);
}
