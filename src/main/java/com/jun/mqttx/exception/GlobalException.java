package com.jun.mqttx.exception;

/**
 * 项目顶级异常
 *
 * @author Jun
 * @date 2020-03-03 21:19
 */
public class GlobalException extends RuntimeException {

    public GlobalException() {
        super();
    }

    public GlobalException(String message) {
        super(message);
    }

    public GlobalException(String message, Throwable cause) {
        super(message, cause);
    }

    public GlobalException(Throwable cause) {
        super(cause);
    }
}
