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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    Mono<Void> saveOut(String clientId, int messageId);

    /**
     * 保存 pubRel 消息 messageId(方向为 client -> broker)
     *
     * @param clientId  客户端ID
     * @param messageId 报文ID
     */
    Mono<Void> saveIn(String clientId, int messageId);

    /**
     * 校验消息是否已存在
     *
     * @param clientId  客户端ID
     * @param messageId 消息ID
     * @return true if msg exist
     */
    Mono<Boolean> isInMsgDup(String clientId, int messageId);

    /**
     * 移除 pubRel 标志(方向为 client -> broker)
     *
     * @param messageId 消息ID
     * @param clientId  客户端ID
     */
    Mono<Void> removeIn(String clientId, int messageId);

    /**
     * 移除 pubRel 标志(方向为 broker -> client)
     *
     * @param messageId 消息ID
     * @param clientId  客户端ID
     */
    Mono<Void> removeOut(String clientId, int messageId);

    /**
     * 获取客户端未完成的 pubRel 消息
     *
     * @param clientId 客户端ID
     * @return 未完成 pubRel messageId 列表
     */
    Flux<Integer> searchOut(String clientId);

    /**
     * 清理掉客户所有的消息
     *
     * @param clientId 客户端ID
     */
    Mono<Void> clear(String clientId);
}
