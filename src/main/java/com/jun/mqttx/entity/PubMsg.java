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

import com.jun.mqttx.utils.Uuids;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * 发布的消息
 *
 * @author Jun
 * @since 1.0.4
 */
@Data
@Accessors(chain = true)
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

    /** 基于时间戳的 uuid str, 用于标记消息 */
    private String uuid;

    /** 判断 payload 是否是共享数据，采用二级寻址方式获取 */
    private boolean payloadSharable = false;

    //@formatter:on

    public static PubMsg of(int qos, String topic, boolean retain, byte[] payload) {
        var uuid = Uuids.timeBased();

        return new PubMsg()
                .setAppointedClientId(null)
                .setQoS(qos)
                .setMessageId(0)
                .setTopic(topic)
                .setRetain(retain)
                .setWillFlag(false)
                .setDup(false)
                .setPayload(payload)
                .setUuid(uuid);
    }

    /**
     * 消息唯一 id.
     */
    public String uniqueId() {
        return uuid;
    }

    /**
     * 检查 payload 是否超过指定阈值
     *
     * @param threshold 阈值
     * @return true 如果载荷大小超过阈值
     */
    public boolean isPayloadSizeOverThreshold(int threshold) {
        if (payload == null) {

        }
        return payload.length > threshold;
    }

    /**
     * 一个新的拷贝
     *
     * @return copy of this instance
     */
    public PubMsg copied() {
        return new PubMsg()
                .setAppointedClientId(appointedClientId)
                .setQoS(qoS)
                .setMessageId(messageId)
                .setTopic(topic)
                .setRetain(retain)
                .setWillFlag(willFlag)
                .setDup(dup)
                .setPayload(payload)
                .setUuid(uuid);
    }
}
