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

package com.jun.mqttx.constants;

/**
 * 集群消息枚举
 *
 * @author Jun
 * @since 1.0.4
 */
public enum InternalMessageEnum {

    PUB(1, ClusterTopic.PUB),

    PUB_ACK(2, ClusterTopic.PUB_ACK),

    PUB_REC(3, ClusterTopic.PUB_REC),

    PUB_COM(4, ClusterTopic.PUB_COM),

    PUB_REL(5, ClusterTopic.PUB_REL),

    DISCONNECT(6, ClusterTopic.DISCONNECT),

    ALTER_USER_AUTHORIZED_TOPICS(7, ClusterTopic.ALTER_USER_AUTHORIZED_TOPICS),

    SUB_UNSUB(8, ClusterTopic.SUB_UNSUB);

    private final int type;

    /**
     * redis pub/sub channel
     */
    private final String channel;

    InternalMessageEnum(int type, String channel) {
        this.type = type;
        this.channel = channel;
    }

    public String getChannel() {
        return channel;
    }

    public int getType() {
        return type;
    }
}