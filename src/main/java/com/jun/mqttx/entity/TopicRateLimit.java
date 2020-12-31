package com.jun.mqttx.entity;

import lombok.Data;

/**
 * 主题限流
 */
@Data
public class TopicRateLimit {
    //@formatter:off

    /** 主题 */
    private String topic;

    /** 容量 */
    private int capacity;

    /** 填充速率 */
    private int replenishRate;

    /** 每次请求消耗令牌数量 */
    private int tokenConsumedPerAcquire;
}
