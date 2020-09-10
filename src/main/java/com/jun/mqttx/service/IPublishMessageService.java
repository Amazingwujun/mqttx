package com.jun.mqttx.service;

import com.jun.mqttx.entity.PubMsg;

import java.util.List;

/**
 * publish msg service
 *
 * @author Jun
 * @since 1.0.4
 */
public interface IPublishMessageService {

    /**
     * 消息ID
     *
     * @param pubMsg   publish 消息体
     * @param clientId 客户id
     */
    void save(String clientId, PubMsg pubMsg);

    /**
     * 清理与客户相关连的 publish 消息
     *
     * @param clientId 客户端id
     */
    void clear(String clientId);

    /**
     * 移除指定的 publish 消息
     *
     * @param clientId  客户端id
     * @param messageId 消息id
     */
    void remove(String clientId, int messageId);

    /**
     * 获取客户关联的 publish message
     *
     * @param clientId 客户端id
     * @return 客户未能完成发送的消息列表
     */
    List<PubMsg> search(String clientId);
}