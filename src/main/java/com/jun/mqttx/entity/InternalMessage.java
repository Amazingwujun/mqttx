package com.jun.mqttx.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 集群消息，用于集群间消息发布
 *
 * @author Jun
 * @since 1.0.4
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InternalMessage<T> {
    //@formatter:off

    /** 数据 */
    private T data;

    /** 时间戳 */
    private long timestamp;

    /** broker id */
    private int brokerId;

    //@formatter:on
}