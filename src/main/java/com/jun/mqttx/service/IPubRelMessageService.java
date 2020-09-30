package com.jun.mqttx.service;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * pubRel 消息相关服务，主要是crud相关
 *
 * @author Jun
 * @since 1.0.4
 */
public interface IPubRelMessageService {

    /**
     * 保存 pubRel 消息 messageId
     *
     * @param clientId  客户端ID
     * @param messageId 报文ID
     */
    void save(String clientId, int messageId);

    /**
     * 校验消息是否已存在
     *
     * @param clientId  客户端ID
     * @param messageId 消息ID
     * @return
     */
    boolean isDupMsg(String clientId, int messageId);

    /**
     * 移除 pubRel 标志
     *
     * @param messageId 消息ID
     * @param clientId  客户端ID
     */
    void remove(String clientId, int messageId);

    /**
     * @see #remove(String, int)
     */
    Mono<Long> asyncRemove(String clientId, int messageId);

    /**
     * 获取客户端未完成的 pubRel 消息
     *
     * @param clientId 客户端ID
     * @return 未完成 pubRel messageId 列表
     */
    List<Integer> search(String clientId);

    /**
     * 清理掉客户所有的消息
     *
     * @param clientId 客户端ID
     */
    void clear(String clientId);
}