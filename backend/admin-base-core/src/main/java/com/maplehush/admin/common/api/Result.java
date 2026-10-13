package com.maplehush.admin.common.api;

import com.maplehush.admin.common.constant.ResultCodes;

/** 与前端约定的统一响应容器，traceId 由响应头携带。 */
public record Result<T>(int code, T data, String error, String message) {
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCodes.SUCCESS, data, null, "ok");
    }

    public static Result<Void> fail(int code, String error, String message) {
        return new Result<>(code, null, error, message);
    }
}
