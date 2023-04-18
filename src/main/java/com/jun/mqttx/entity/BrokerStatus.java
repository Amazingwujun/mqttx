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

import com.jun.mqttx.utils.JSON;
import lombok.Builder;
import lombok.Getter;

/**
 * 系统主题状态
 */
@Getter
@Builder
public class BrokerStatus {
    //@formatter:off

    /** broker 当前活动连接 */
    private final Integer activeConnectCount;

    /** 时间戳 */
    private final String timestamp;

    /** 版本号 */
    private final String version;

    /** 最大活动连接数 */
    private final Integer maxActiveConnectCount;

    /** @see  com.jun.mqttx.broker.handler.ProbeHandler#IN_MSG_SIZE */
    private final Integer receivedMsg;

    /** @see com.jun.mqttx.broker.handler.ProbeHandler#OUT_MSG_SIZE */
    private final Integer sendMsg;

    private final Integer uptime;

    //@formatter:on

    /**
     * 将当前对象转为 json 格式字节数组
     *
     * @return json 格式字节数组
     */
    public byte[] toJsonBytes() {
        return JSON.writeValueAsBytes(this);
    }
}
