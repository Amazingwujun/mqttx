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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * topic 订阅的用户信息.
 * <pre>
 * If a Server receives a SUBSCRIBE Packet containing a Topic Filter that is identical
 * to an existing Subscription’s Topic Filter then it MUST completely replace that existing
 * Subscription with a new Subscription. The Topic Filter in the new Subscription will be
 * identical to that in the previous Subscription, although its maximum QoS value could be different.
 * Any existing retained messages matching the Topic Filter MUST be re-sent, but the flow of
 * publications MUST NOT be interrupted [MQTT-3.8.4-3].
 * </pre>
 * 根据上述协议，client 订阅对象判定相等只需 topic 与 client 两个参数即可。但由于共享订阅的支持，判断 ClientSub 是否相等需要增加
 * {@link #shareName} 字段的判断
 *
 * @author Jun
 * @since 1.0.4
 */
@Data
public class ClientSub implements Comparable<ClientSub> {

    public boolean cleanSession;
    private String clientId;
    private int qos;
    private String topic;
    private String shareName;

    public static ClientSub of(String clientId, int qos, String topic, boolean cleanSession) {
        return of(clientId, qos, topic, cleanSession, null);
    }

    public static ClientSub of(String clientId, int qos, String topic, boolean cleanSession, String shareName) {
        ClientSub clientSub = new ClientSub();
        clientSub.setClientId(clientId);
        clientSub.setQos(qos);
        clientSub.setTopic(topic);
        clientSub.setCleanSession(cleanSession);
        clientSub.setShareName(shareName);

        return clientSub;
    }

    @JsonIgnore
    public boolean isShareSub() {
        return StringUtils.hasText(shareName);
    }

    public Boolean notShareSub() {
        return !isShareSub();
    }

    /**
     * 共享订阅发布机制需要有序的集合,对象按 {@link ClientSub#clientId#hashCode()} 排序.
     *
     * @param o 比较对象
     */
    @Override
    public int compareTo(ClientSub o) {
        if (o != null) {
            return this.clientId.compareTo(o.getClientId());
        } else {
            throw new IllegalArgumentException("非法的比较对象:" + o);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientSub clientSub = (ClientSub) o;
        return Objects.equals(clientId, clientSub.clientId) && Objects.equals(topic, clientSub.topic) && Objects.equals(shareName, clientSub.shareName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, topic, shareName);
    }
}
