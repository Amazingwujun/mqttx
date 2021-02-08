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

import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.entity.PubMsg;
import com.jun.mqttx.entity.SourcePayload;

import java.util.List;

/**
 * publish msg service
 *
 * @author Jun
 * @since 1.0.4
 */
public interface IPublishMessageService {

    /**
     * 持久化用户发布消息
     *
     * @param pubMsg   publish 消息体
     * @param clientId 客户id
     */
    void save(String clientId, PubMsg pubMsg);

    /**
     * 存储 {@link SourcePayload} 及 client 与 {@link SourcePayload#getPayload()} 的关系
     *
     * @param clientSubs 订阅客户端id集合
     * @param sourcePayload 待发布消息原始数据
     */
    void savePayloadAndClientBinding(List<String> clientSubs, SourcePayload sourcePayload);

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