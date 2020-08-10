package com.jun.mqttx.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客户端订阅或解除订阅消息, 用于集群内部广播
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientSubOrUnsubMsg {

    private String clientId;

    private int qos;

    private String topic;

    /**
     * 当 {@code type} == 2, topics 不能为空
     */
    private List<String> topics;

    /**
     * Defined in {@link com.jun.mqttx.service.impl.SubscriptionServiceImpl}
     * <ol>
     *     <li>1 -> 订阅</li>
     *     <li>2 -> 解除客户订阅</li>
     *     <li>3 -> 移除topic</li>
     * </ol>
     */
    private int type;
}
