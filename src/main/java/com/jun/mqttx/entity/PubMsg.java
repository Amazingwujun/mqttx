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

package com.jun.mqttx.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发布的消息
 *
 * @author Jun
 * @since 1.0.4
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PubMsg {
    //@formatter:off

    /**
     * 共享主题钦定的客户端 id, 如果此字段不为空，表明消息只能发给指定的 client
     */
    private String appointedClientId;

    /**
     * <ol>
     *     <li>0 -> at most once</li>
     *     <li>1 -> at least once</li>
     *     <li>2 -> exactly once</li>
     * </ol>
     */
    private int qoS;

    /**
     * sometimes we also named it <b>packetId</b>
     */
    private int messageId;

    private String topic;

    private boolean retain;

    /** 消息是否为遗嘱消息 */
    private boolean willFlag;

    /** 是否为 dup 消息 */
    private boolean dup;

    private byte[] payload;

    //@formatter:on

    public static PubMsg of(int qos, String topic, boolean retain, byte[] payload) {
        return new PubMsg(null, qos, 0, topic, retain, false, false, payload);
    }
}