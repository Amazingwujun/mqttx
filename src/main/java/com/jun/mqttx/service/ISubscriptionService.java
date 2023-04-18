/*
 * Copyright 2020-2023 the original author or authors.
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 订阅相关服务, 为两种主题提供服务:
 * <ol>
 *     <li>普通主题</li>
 *     <li>系统主题</li>
 * </ol>
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
    Mono<Void> subscribe(ClientSub clientSub);

    /**
     * 解除订阅
     *
     * @param clientId 客户id
     * @param topics   主题列表
     */
    Mono<Void> unsubscribe(String clientId, boolean cleanSession, List<String> topics);

    /**
     * 获取订阅了 topic 的客户id
     *
     * @param topic 主题
     * @return 订阅了主题的客户id列表
     */
    Flux<ClientSub> searchSubscribeClientList(String topic);

    /**
     * 移除客户订阅
     *
     * @param clientId     客户ID
     * @param cleanSession 清理类型，true 清理 cleanSession = 1, false清理 cleanSession = 0
     */
    Mono<Void> clearClientSubscriptions(String clientId, boolean cleanSession);

    /**
     * 移除未包含在 authorizedSub 集合中的客户端订阅
     *
     * @param clientId      客户端ID
     * @param authorizedSub 客户端被允许订阅的 topic 集合
     */
    Mono<Void> clearUnAuthorizedClientSub(String clientId, List<String> authorizedSub);


    /**
     * 获取订阅系统主题 topic 的客户端集合
     *
     * @param topic 系统主题
     */
    Flux<ClientSub> searchSysTopicClients(String topic);

    /**
     * 保存系统主题客户订阅
     *
     * @param clientSub 客户订阅
     */
    Mono<Void> subscribeSys(ClientSub clientSub);

    /**
     * 解除客户系统主题订阅
     *
     * @param clientId 客户 id
     * @param topics   主题列表
     */
    Mono<Void> unsubscribeSys(String clientId, List<String> topics);

    /**
     * 清理客户订阅的系统主题
     *
     * @param clientId 客户 id
     */
    Mono<Void> clearClientSysSub(String clientId);
}
