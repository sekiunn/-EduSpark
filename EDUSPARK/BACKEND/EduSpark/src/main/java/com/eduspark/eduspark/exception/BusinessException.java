package com.eduspark.eduspark.exception;

import lombok.Getter;

/**
 * 业务异常
 * 继承 RuntimeException，支持统一异常处理
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误信息
     */
    private final String message;

    /**
     * 构造函数（使用错误码枚举）
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    /**
     * 构造函数（使用错误码枚举 + 自定义消息）
     */
    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.code = errorCode.getCode();
        this.message = customMessage;
    }

    /**
     * 构造函数（自定义错误码 + 消息）
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    /**
     * 构造函数（消息）
     */
    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.SERVER_ERROR.getCode();
        this.message = message;
    }

    /**
     * 构造函数（原因）
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.SERVER_ERROR.getCode();
        this.message = message;
    }

    /**
     * 构造函数（错误码 + 原因）
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    /**
     * 构造函数（错误码 + 自定义消息 + 原因）
     */
    public BusinessException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.code = errorCode.getCode();
        this.message = customMessage;
    }

    @Override
    public String toString() {
        return "BusinessException{" +
                "code=" + code +
                ", message='" + message + '\'' +
                '}';
    }
}
