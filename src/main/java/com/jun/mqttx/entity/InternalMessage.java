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

/**
 * 集群消息，用于集群间消息发布
 *
 * @author Jun
 * @since 1.0.4
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InternalMessage<T> {
    //@formatter:off

    /** 数据 */
    private T data;

    /** 时间戳 */
    private long timestamp;

    /** broker id */
    private String brokerId;

    //@formatter:on
}
