package com.jun.mqttx.entity;

import lombok.Data;

import java.util.List;

/**
 * 认证对象. 建议 {@link Authentication#clientId} equals {@link Authentication#username}, 以避免之前版本使用 username 作为
 * 默认 clientId 导致的 bug.
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

    private String clientId;

    private String username;

    private String password;
}
