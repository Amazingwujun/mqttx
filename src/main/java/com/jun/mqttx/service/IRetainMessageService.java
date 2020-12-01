package com.jun.mqttx.service;

import com.jun.mqttx.entity.PubMsg;

import java.util.List;

/**
 * retain 消息服务
 *
 * @author Jun
 * @since 1.0.4
 */
public interface IRetainMessageService {

    /**
     * 搜索匹配 topicFilter 的 retain 消息列表
     *
     * @param newSubTopic 客户端新订阅主题
     * @return 匹配的消息列表
     */
    List<PubMsg> searchListByTopicFilter(String newSubTopic);

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