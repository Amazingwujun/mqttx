package com.jun.mqttx.exception;

/**
 * 权限异常
 *
 * @author Jun
 * @since 1.0.4
 */
public class AuthorizationException extends GlobalException {

    public AuthorizationException() {
    }

    public AuthorizationException(String message) {
        super(message);
    }
}