package com.jun.mqttx.service;

import com.jun.mqttx.entity.Session;

/**
 * 会话相关业务
 *
 * @author Jun
 * @since 1.0.4
 */
public interface ISessionService {

    /**
     * 存储
     *
     * @param session {@link Session}
     */
    void save(Session session);

    /**
     * 通过 clientId 获取会话
     *
     * @param clientId 客户端ID
     * @return {@link Session}
     */
    Session find(String clientId);

    /**
     * 清理会话
     *
     * @param clientId 客户端ID
     */
    void clear(String clientId);

    /**
     * 检查 clientId 关联的 session 是否存在
     *
     * @param clientId 客户端ID
     * @return true if session exist
     */
    boolean hasKey(String clientId);

    /**
     * 获取 client 的下一个 messageId
     *
     * @param clientId 客户端ID
     * @return next message id
     */
    int nextMessageId(String clientId);
}