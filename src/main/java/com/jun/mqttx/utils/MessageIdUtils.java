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

package com.jun.mqttx.utils;


import java.util.HashMap;
import java.util.Map;

/**
 * 提供 messageId 生成工具, 供测试模式使用
 *
 * @since 1.0.6
 */
public class MessageIdUtils {

    private static final Map<String, Integer> map = new HashMap<>();

    public synchronized static int nextMessageId(String clientId) {
        int id = map.computeIfAbsent(clientId, k -> 1);
        if ((id & 0xffff) == 0) {
            id = 1;
        } else {
            id++;
        }
        map.put(clientId, id);
        return trimMessageId(id);
    }

    /**
     * 此方法用于处理大于 65536 的消息 id. (大于 65536 的消息 id 会导致该消息无法被正常移除的 bug)
     *
     * @param messageId 需要进行截取
     * @return 截取后的消息 id
     */
    public static int trimMessageId(int messageId) {
        return messageId & 0xffff;
    }
}
