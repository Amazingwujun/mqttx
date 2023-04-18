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

import com.jun.mqttx.entity.PubMsg;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * publish msg service
 *
 * @author Jun
 * @since 1.0.4
 */
public interface IPublishMessageService {

    /**
     * 消息ID
     *
     * @param pubMsg   publish 消息体
     * @param clientId 客户id
     */
    Mono<Void> save(String clientId, PubMsg pubMsg);

    /**
     * 清理与客户相关连的 publish 消息
     *
     * @param clientId 客户端id
     */
    Mono<Void> clear(String clientId);

    /**
     * 移除指定的 publish 消息
     *
     * @param clientId  客户端id
     * @param messageId 消息id
     */
    Mono<Void> remove(String clientId, int messageId);

    /**
     * 获取客户关联的 publish message
     *
     * @param clientId 客户端id
     * @return 客户未能完成发送的消息列表
     */
    Flux<PubMsg> search(String clientId);
}
