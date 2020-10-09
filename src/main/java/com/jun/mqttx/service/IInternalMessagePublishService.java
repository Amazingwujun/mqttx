package com.jun.mqttx.service;

import com.jun.mqttx.entity.InternalMessage;
import reactor.core.publisher.Mono;

/**
 * 内部消息发布服务
 *
 * @author Jun
 * @since 1.0.4
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

    /**
     * @see #publish(InternalMessage, String)
     */
    <T> Mono<Long> asyncPublish(InternalMessage<T> internalMessage, String channel);
}