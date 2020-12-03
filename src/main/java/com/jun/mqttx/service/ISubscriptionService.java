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

import java.util.List;

/**
 * 订阅相关服务
 *
 * @author Jun
 * @since 1.0.4
 */
public interface ISubscriptionService {

    /**
     * 保存客户订阅的主题
     *
     * @param clientSub 客户订阅
     */
    void subscribe(ClientSub clientSub);

    /**
     * 解除订阅
     *
     * @param clientId 客户id
     * @param topics   主题列表
     */
    void unsubscribe(String clientId, List<String> topics);

    /**
     * 获取订阅了 topic 的客户id
     *
     * @param topic 主题
     * @return 订阅了主题的客户id列表
     */
    List<ClientSub> searchSubscribeClientList(String topic);

    /**
     * 移除客户订阅
     *
     * @param clientId 客户ID
     */
    void clearClientSubscriptions(String clientId);

    /**
     * 移除指定 topic
     *
     * @param topic 主题
     */
    void removeTopic(String topic);

    /**
     * 移除未包含在 authorizedSub 集合中的客户端订阅
     *
     * @param clientId      客户端ID
     * @param authorizedSub 客户端被允许订阅的 topic 集合
     */
    void clearClientSub(String clientId, List<String> authorizedSub);
}