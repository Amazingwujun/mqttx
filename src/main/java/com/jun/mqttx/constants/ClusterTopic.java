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
 * 集群主题
 */
public interface ClusterTopic {

    String PUB = "MQTTX_INTERNAL_PUB";

    String PUB_ACK = "MQTTX_INTERNAL_PUBACK";

    String PUB_REC = "MQTTX_INTERNAL_PUBREC";

    String PUB_COM = "MQTTX_INTERNAL_PUBCOM";

    String PUB_REL = "MQTTX_INTERNAL_PUBREL";

    String DISCONNECT = "MQTTX_INTERNAL_DISCONNECT";

    String ALTER_USER_AUTHORIZED_TOPICS = "MQTTX_ALTER_USER_AUTHORIZED_TOPICS";

    String SUB_UNSUB = "MQTTX_INTERNAL_SUB_OR_UNSUB";
}
