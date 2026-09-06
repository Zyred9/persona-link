package com.personalink.server.dto;

/**
 * 统一接口响应。
 *
 * @param code 业务状态码
 * @param message 响应消息
 * @param data 响应数据
 * @param <T> 数据类型
 */
public record ApiResponse<T>(int code, String message, T data) {

    /**
     * 创建成功响应。
     *
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    /**
     * 创建失败响应。
     *
     * @param code 业务状态码
     * @param message 错误消息
     * @return 失败响应
     */
    public static ApiResponse<Void> failure(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
