package com.jun.mqttx.service;

import com.jun.mqttx.entity.InternalMessage;

/**
 * 内部消息发布服务
 *
 * @author Jun
 * @date 2020-05-14 10:27
 */
public interface IInternalMessagePublishService {

    /**
     * 发布集群消息
     *
     * @param internalMessage {@link InternalMessage}
     * @param <T>             {@link InternalMessage#getData()} 类别
     * @param channel         推送频道
     */
    <T> void publish(InternalMessage<T> internalMessage, String channel);
}
