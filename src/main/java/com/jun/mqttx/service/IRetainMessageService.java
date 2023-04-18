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
 * retain 消息服务
 *
 * @author Jun
 * @since 1.0.4
 */
public interface IRetainMessageService {

    /**
     * 搜索匹配 topicFilter 的 retain 消息列表
     *
     * @param newSubTopic 客户端新订阅主题
     * @return 匹配的消息列表
     */
    Flux<PubMsg> searchListByTopicFilter(String newSubTopic);

    /**
     * 存储当前 topic 的 retain 消息
     *
     * @param topic  主题
     * @param pubMsg 发布消息
     */
    Mono<Void> save(String topic, PubMsg pubMsg);

    /**
     * 移除 topic 的 retain 消息
     *
     * @param topic 主题
     */
    Mono<Void> remove(String topic);

    /**
     * 获取订阅主题的保留信息
     *
     * @param topic 主题
     * @return {@link PubMsg}
     */
    Mono<PubMsg> get(String topic);
}
