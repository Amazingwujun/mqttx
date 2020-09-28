package com.jun.mqttx.entity;

import com.alibaba.fastjson.JSON;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 系统主题状态
 */
@Getter
public class BrokerStatus {
    //@formatter:off

    /** broker 当前活动连接 */
    private Integer activeConnectCount;

    /** 时间戳 */
    private String timestamp;

    /** 版本号 */
    private String version;

    //@formatter:on

    private BrokerStatus(Integer activeConnectCount, String timestamp, String version) {
        this.activeConnectCount = activeConnectCount;
        this.timestamp = timestamp;
        this.version = version;
    }

    public static BrokerStatus of(Integer activeConnected, String version) {
        return of(activeConnected, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), version);
    }

    public static BrokerStatus of(Integer activeConnected, String timestamp, String version) {
        return new BrokerStatus(activeConnected, timestamp, version);
    }

    public byte[] toUtf8Bytes() {
        return JSON.toJSONString(this).getBytes(StandardCharsets.UTF_8);
    }
}