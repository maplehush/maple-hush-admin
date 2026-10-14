package com.maplehush.admin.common.exception;

import com.maplehush.admin.common.constant.ResultCodes;

import org.springframework.http.HttpStatus;

/**
 * 公共错误码；业务错误码在业务模块扩展
 */
public enum ErrorCode {
    /**
     * 请求格式错误，例如 JSON 无法解析或参数类型错误
     */
    BAD_REQUEST(ResultCodes.BAD_REQUEST, "BAD_REQUEST", HttpStatus.BAD_REQUEST),
    /**
     * 请求参数不符合业务校验规则
     */
    INVALID_ARGUMENT(ResultCodes.INVALID_ARGUMENT, "INVALID_ARGUMENT", HttpStatus.BAD_REQUEST),
    /**
     * 验证码错误、过期或已被消费
     */
    CAPTCHA_INVALID(ResultCodes.CAPTCHA_INVALID, "CAPTCHA_INVALID", HttpStatus.BAD_REQUEST),
    /**
     * 未登录、凭据无效或会话已失效
     */
    UNAUTHENTICATED(ResultCodes.UNAUTHENTICATED, "UNAUTHENTICATED", HttpStatus.UNAUTHORIZED),
    /**
     * 身份有效，但没有执行该操作的权限
     */
    FORBIDDEN(ResultCodes.FORBIDDEN, "FORBIDDEN", HttpStatus.FORBIDDEN),
    /**
     * CSRF 凭据缺失、无效或请求来源校验失败
     */
    CSRF_INVALID(ResultCodes.CSRF_INVALID, "CSRF_INVALID", HttpStatus.FORBIDDEN),
    /**
     * 目标资源不存在或已被删除
     */
    NOT_FOUND(ResultCodes.NOT_FOUND, "NOT_FOUND", HttpStatus.NOT_FOUND),
    /**
     * 唯一约束、资源关联或并发状态产生冲突
     */
    CONFLICT(ResultCodes.CONFLICT, "CONFLICT", HttpStatus.CONFLICT),
    /**
     * 操作超过账号或 IP 的频率限制
     */
    RATE_LIMITED(ResultCodes.RATE_LIMITED, "RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS),
    /**
     * Redis 等必要依赖暂不可用
     */
    DEPENDENCY_UNAVAILABLE(
            ResultCodes.DEPENDENCY_UNAVAILABLE,
            "DEPENDENCY_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE),
    /**
     * 未预期的服务器内部错误；对客户端隐藏实现细节
     */
    INTERNAL_ERROR(ResultCodes.INTERNAL_ERROR, "INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);

    /**
     * 响应体中的稳定业务数字码；不同于 HTTP 状态码
     */
    private final int code;

    /**
     * 响应体 error 字段的稳定标识，供前端识别错误类型
     */
    private final String value;

    /**
     * 此错误对应的 HTTP 响应状态
     */
    private final HttpStatus status;

    ErrorCode(int code, String value, HttpStatus status) {
        this.code = code;
        this.value = value;
        this.status = status;
    }

    /**
     * @return 响应体中的业务数字码
     */
    public int code() {
        return code;
    }

    /**
     * @return 响应体 error 字段的稳定标识
     */
    public String value() {
        return value;
    }

    /**
     * @return 错误应使用的 HTTP 响应状态
     */
    public HttpStatus status() {
        return status;
    }
}
