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

    private List<String> authorizedTopics;

    private String username;

    private String password;
}
