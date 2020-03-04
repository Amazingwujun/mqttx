package com.jun.mqttx.exception;

/**
 * 证书相关异常
 *
 * @author Jun
 * @date 2020-03-03 23:34
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
