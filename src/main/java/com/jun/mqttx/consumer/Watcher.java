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

package com.jun.mqttx.consumer;

import com.jun.mqttx.constants.InternalMessageEnum;

/**
 * 观察者，实现此接口.
 *
 * @author Jun
 * @since 1.0.4
 */
public interface Watcher {

    /**
     * 每当有新的集群消息达到是，触发行为。
     * 注意：实现方法不应该有耗时操作(e.g. 访问数据库)
     *
     * @param msg 集群消息
     */
    void action(byte[] msg);

    /**
     * Watcher 支持的 channel 类别
     *
     * @param channel {@link InternalMessageEnum}
     * @return true if Watcher support
     */
    boolean support(String channel);
}
