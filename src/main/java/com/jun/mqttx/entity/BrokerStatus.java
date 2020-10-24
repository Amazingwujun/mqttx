package com.jun.mqttx.entity;

import com.alibaba.fastjson.JSON;
import lombok.Builder;
import lombok.Getter;

import java.nio.charset.StandardCharsets;

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


    public byte[] toUtf8Bytes() {
        return JSON.toJSONString(this).getBytes(StandardCharsets.UTF_8);
    }
}