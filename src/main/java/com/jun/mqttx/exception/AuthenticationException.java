package com.jun.mqttx.exception;

/**
 * 客户端认证异常
 *
 * @author Jun
 * @since 1.0.4
 */
public class AuthenticationException extends GlobalException {
    public AuthenticationException() {
    }

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthenticationException(Throwable cause) {
        super(cause);
    }
}