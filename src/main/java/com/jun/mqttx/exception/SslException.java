package com.jun.mqttx.exception;

/**
 * 证书相关异常
 *
 * @author Jun
 * @since 1.0.4
 */
public class SslException extends GlobalException {

    public SslException() {
    }

    public SslException(String message) {
        super(message);
    }

    public SslException(String message, Throwable cause) {
        super(message, cause);
    }

    public SslException(Throwable cause) {
        super(cause);
    }
}