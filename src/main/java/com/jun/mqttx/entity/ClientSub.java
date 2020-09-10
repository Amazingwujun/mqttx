package com.jun.mqttx.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * topic 订阅的用户信息
 *
 * @author Jun
 * @since 1.0.4
 */
@Data
@AllArgsConstructor
public class ClientSub implements Comparable<ClientSub> {

    private String clientId;

    private int qos;

    private String topic;

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