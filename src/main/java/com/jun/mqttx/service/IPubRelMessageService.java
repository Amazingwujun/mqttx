package com.jun.mqttx.service;

import java.util.List;

/**
 * pubRel 消息相关服务，主要是crud相关.
 * <p>
 * 消息分两种：
 * <ol>
 *     <li>IN: client => broker, 由 client 主动发送 publish 消息给 broker</li>
 *     <li>OUT: broker => client, 由 broker 推送 publish 消息给 client</li>
 * </ol>
 * IN/OUT 表示相对消息相对 broker 是输入还是输出.
 *
 * @author Jun
 * @since 1.0.4
 */
public interface IPubRelMessageService {

    /**
     * 保存 pubRel 消息 messageId(方向为 broker -> client)
     *
     * @param clientId  客户端ID
     * @param messageId 报文ID
     */
    void saveOut(String clientId, int messageId);

    /**
     * 保存 pubRel 消息 messageId(方向为 client -> broker)
     *
     * @param clientId  客户端ID
     * @param messageId 报文ID
     */
    void saveIn(String clientId, int messageId);

    /**
     * 校验消息是否已存在
     *
     * @param clientId  客户端ID
     * @param messageId 消息ID
     * @return true if msg exist
     */
    boolean isInMsgDup(String clientId, int messageId);

    /**
     * 移除 pubRel 标志(方向为 client -> broker)
     *
     * @param messageId 消息ID
     * @param clientId  客户端ID
     */
    void removeIn(String clientId, int messageId);

    /**
     * 移除 pubRel 标志(方向为 broker -> client)
     *
     * @param messageId 消息ID
     * @param clientId  客户端ID
     */
    void removeOut(String clientId, int messageId);

    /**
     * 获取客户端未完成的 pubRel 消息
     *
     * @param clientId 客户端ID
     * @return 未完成 pubRel messageId 列表
     */
    List<Integer> searchOut(String clientId);

    /**
     * 清理掉客户所有的消息
     *
     * @param clientId 客户端ID
     */
    void clear(String clientId);
}