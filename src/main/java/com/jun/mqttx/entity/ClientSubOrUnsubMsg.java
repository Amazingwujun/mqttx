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

package com.jun.mqttx.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客户端订阅或解除订阅消息, 用于集群内部广播
 *
 * @author Jun
 * @since 1.0.4
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientSubOrUnsubMsg {

    private String clientId;

    private int qos;

    private String topic;

    private boolean cleanSession;

    /**
     * 当 {@code type} == 2, topics 不能为空
     */
    private List<String> topics;

    /**
     * Defined in {@link com.jun.mqttx.service.impl.DefaultSubscriptionServiceImpl}
     * <ol>
     *     <li>1 -> 订阅</li>
     *     <li>2 -> 解除客户订阅</li>
     *     <li>3 -> 移除topic</li>
     * </ol>
     */
    private int type;
}
