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

import lombok.Data;

/**
 * topic 订阅的用户信息
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

    public static ClientSub of(String clientId, int qos, String topic, boolean cleanSession) {
        ClientSub clientSub = new ClientSub();
        clientSub.setClientId(clientId);
        clientSub.setQos(qos);
        clientSub.setTopic(topic);
        clientSub.setCleanSession(cleanSession);

        return clientSub;
    }

    /**
     * 共享订阅发布机制需要有序的集合,对象按 {@link ClientSub#clientId#hashCode()} 排序.
     *
     * @param o 比较对象
     */
    @Override
    public int compareTo(ClientSub o) {
        if (o != null) {
            return clientId.hashCode() - o.hashCode();
        } else {
            throw new IllegalArgumentException("非法的比较对象:" + o);
        }
    }
}