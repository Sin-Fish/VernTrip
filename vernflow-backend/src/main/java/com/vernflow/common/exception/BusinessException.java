package com.vernflow.common.exception;

/**
 * 业务异常。
 */
public class BusinessException extends RuntimeException {

    /**
     * 使用业务消息创建异常。
     *
     * @param message 异常消息
     */
    public BusinessException(String message) {
        super(message);
    }
}
