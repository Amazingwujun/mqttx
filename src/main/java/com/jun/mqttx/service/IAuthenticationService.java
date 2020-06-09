package com.jun.mqttx.service;

import com.jun.mqttx.entity.Authentication;
import com.jun.mqttx.exception.AuthenticationException;
import com.jun.mqttx.exception.AuthorizationException;

/**
 * 客户端认证服务
 *
 * @author Jun
 * @date 2020-03-04 11:33
 */
public interface IAuthenticationService {

    /**
     * 执行客户认证
     *
     * @param username 用户名
     * @param password 密码
     * @throws AuthenticationException if authenticate failed
     * @throws AuthorizationException  if client
     */
    Authentication authenticate(String username, byte[] password) throws AuthenticationException, AuthorizationException;
}
