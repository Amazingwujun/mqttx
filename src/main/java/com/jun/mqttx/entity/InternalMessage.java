package com.jun.mqttx.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 集群消息，用于集群间消息发布
 *
 * @author Jun
 * @date 2020-05-14 09:09
 */
@Data
@AllArgsConstructor
public class InternalMessage<T> {
    //@formatter:off

    /** 数据 */
    private T data;

    /** 时间戳 */
    private long timestamp;

    /**
     * broker id
     */
    private int brokerId;

    //@formatter:on
}
