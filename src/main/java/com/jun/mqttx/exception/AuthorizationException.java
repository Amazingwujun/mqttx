package com.jun.mqttx.exception;

/**
 * 权限异常
 *
 * @author Jun
 * @date 2020-03-04 20:26
 */
public class AuthorizationException extends GlobalException {

    public AuthorizationException() {
    }

    public AuthorizationException(String message) {
        super(message);
    }
}
