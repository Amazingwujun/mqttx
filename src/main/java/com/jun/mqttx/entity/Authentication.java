package com.jun.mqttx.entity;

import lombok.Data;

import java.util.List;

/**
 * 认证对象
 *
 * @author Jun
 * @date 2020-06-09 16:13
 */
@Data
public class Authentication {

    /**
     * 允许订阅的 topic filter
     */
    private List<String> authorizedSub;

    /**
     * 允许发布的 topic filter
     */
    private List<String> authorizedPub;

    private String username;

    private String password;
}
