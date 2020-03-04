package com.jun.mqttx.service;

import com.jun.mqttx.entity.Session;

/**
 * 会话相关业务
 *
 * @author Jun
 * @date 2020-03-04 13:58
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
}
