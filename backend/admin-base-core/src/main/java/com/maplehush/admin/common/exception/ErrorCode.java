package com.maplehush.admin.common.exception;

import com.maplehush.admin.common.constant.ResultCodes;
import org.springframework.http.HttpStatus;

/** 公共错误码；业务错误码在业务模块扩展。 */
public enum ErrorCode {
    BAD_REQUEST(ResultCodes.BAD_REQUEST, "BAD_REQUEST", HttpStatus.BAD_REQUEST),
    INVALID_ARGUMENT(ResultCodes.INVALID_ARGUMENT, "INVALID_ARGUMENT", HttpStatus.BAD_REQUEST),
    CAPTCHA_INVALID(ResultCodes.CAPTCHA_INVALID, "CAPTCHA_INVALID", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(ResultCodes.UNAUTHENTICATED, "UNAUTHENTICATED", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(ResultCodes.FORBIDDEN, "FORBIDDEN", HttpStatus.FORBIDDEN),
    CSRF_INVALID(ResultCodes.CSRF_INVALID, "CSRF_INVALID", HttpStatus.FORBIDDEN),
    NOT_FOUND(ResultCodes.NOT_FOUND, "NOT_FOUND", HttpStatus.NOT_FOUND),
    CONFLICT(ResultCodes.CONFLICT, "CONFLICT", HttpStatus.CONFLICT),
    RATE_LIMITED(ResultCodes.RATE_LIMITED, "RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS),
    DEPENDENCY_UNAVAILABLE(ResultCodes.DEPENDENCY_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(ResultCodes.INTERNAL_ERROR, "INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String value;
    private final HttpStatus status;

    ErrorCode(int code, String value, HttpStatus status) {
        this.code = code;
        this.value = value;
        this.status = status;
    }

    public int code() { return code; }
    public String value() { return value; }
    public HttpStatus status() { return status; }
}
