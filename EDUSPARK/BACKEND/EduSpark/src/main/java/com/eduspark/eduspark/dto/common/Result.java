package com.eduspark.eduspark.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一返回结果
 *
 * @param <T> 数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private T data;

    /**
     * 时间戳
     */
    private Long timestamp;

    // ==================== 成功响应 ====================

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return success((T) null);
    }

    /**
     * 成功响应（有数据）。注意：原来还有一个 {@code success(String message)} 重载，
     * Java 重载解析对 String 偏好"具体类型"——会让 {@code Result.success(urlString)} 把 URL
     * 错误地写到 message 字段、data=null。已删除该重载；如需"仅返回成功消息"请用
     * {@link #successMessage(String)}.
     */
    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> Result<T> success(String message, T data) {
        return Result.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 成功响应，只设 message 字段、data=null。
     * 用于"删除成功 / 操作完成"这种没有具体数据的提示型响应。
     */
    public static <T> Result<T> successMessage(String message) {
        return Result.<T>builder()
                .code(200)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // ==================== 错误响应 ====================

    /**
     * 错误响应
     */
    public static <T> Result<T> error(Integer code, String message) {
        return Result.<T>builder()
                .code(code)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 参数错误
     */
    public static <T> Result<T> badRequest(String message) {
        return error(400, message);
    }

    /**
     * 未授权
     */
    public static <T> Result<T> unauthorized(String message) {
        return error(401, message);
    }

    /**
     * 禁止访问
     */
    public static <T> Result<T> forbidden(String message) {
        return error(403, message);
    }

    /**
     * 资源不存在
     */
    public static <T> Result<T> notFound(String message) {
        return error(404, message);
    }

    /**
     * 服务器内部错误
     */
    public static <T> Result<T> serverError(String message) {
        return error(500, message);
    }

    // ==================== 业务错误 ====================

    /**
     * 业务错误
     */
    public static <T> Result<T> fail(String message) {
        return error(400, message);
    }
}
