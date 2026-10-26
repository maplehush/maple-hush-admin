package com.maplehush.admin.common.api;

import com.maplehush.admin.common.constant.ResultCodes;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 与前端约定的统一响应容器，traceId 由响应头携带
 *
 * @param <T> 业务数据类型
 * @param code 稳定业务响应码；0 表示成功
 * @param data 成功时的业务数据，失败时为空
 * @param error 失败时的稳定错误标识，成功时为空
 * @param message 可向用户展示的安全提示，不包含内部异常详情
 */
public record Result<T>(@Schema(description = "稳定业务码，0 表示成功", example = "0") int code,
        @Schema(description = "业务响应数据；失败时为空") T data,
        @Schema(description = "稳定错误标识；成功时为空") String error,
        @Schema(description = "安全提示消息", example = "ok") String message) {
    /**
     * 构造统一成功响应
     *
     * @param data 业务数据，可为空
     * @param <T> 业务数据类型
     * @return code 为 0、error 为空的成功响应
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCodes.SUCCESS, data, null, "ok");
    }

    /**
     * 构造统一失败响应；HTTP 状态由异常处理器或调用方另行设置
     *
     * @param code 稳定的非零业务错误码
     * @param error 稳定错误标识
     * @param message 可公开展示的安全提示
     * @return data 为空的失败响应
     */
    public static Result<Void> fail(int code, String error, String message) {
        return new Result<>(code, null, error, message);
    }
}
