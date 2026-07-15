package com.vernflow.common.response;

/**
 * 统一接口响应结构。
 *
 * @param success 请求是否成功
 * @param message 响应说明
 * @param data 响应数据
 * @param <T> 数据类型
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    /**
     * 构造成功响应。
     *
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "ok", data);
    }

    /**
     * 构造带消息的成功响应。
     *
     * @param message 响应消息
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /**
     * 构造失败响应。
     *
     * @param message 错误消息
     * @return 失败响应
     */
    public static ApiResponse<Void> failure(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
