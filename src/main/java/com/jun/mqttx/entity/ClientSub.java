package com.jun.mqttx.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * topic 订阅的用户信息
 *
 * @author Jun
 * @date 2020-03-10 11:22
 */
@Data
@AllArgsConstructor
public class ClientSub {

    private String clientId;

    private int qos;
}
