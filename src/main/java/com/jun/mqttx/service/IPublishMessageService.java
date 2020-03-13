package com.jun.mqttx.service;

import com.jun.mqttx.entity.PubMsg;

/**
 * publish msg service
 *
 * @author Jun
 * @date 2020-03-13 14:31
 */
public interface IPublishMessageService {

    /**
     * 消息ID
     *
     * @param pubMsg publish 消息体
     */
    void save(PubMsg pubMsg);

    /**
     * 清理与客户相关连的 publish 消息
     *
     * @param clientId 客户端id
     */
    void clear(String clientId);
}
