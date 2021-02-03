/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
     * @return true if session exist
     */
    boolean clear(String clientId);

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