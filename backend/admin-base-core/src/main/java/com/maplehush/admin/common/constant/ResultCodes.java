package com.maplehush.admin.common.constant;

/**
 * 全局稳定响应码
 */
public final class ResultCodes {
    /**
     * 请求成功
     */
    public static final int SUCCESS = 0;

    /**
     * 请求格式错误，例如 JSON 无法解析或参数类型错误
     */
    public static final int BAD_REQUEST = 40000;

    /**
     * 请求参数不符合业务校验规则
     */
    public static final int INVALID_ARGUMENT = 40010;

    /**
     * 验证码错误、过期或已被消费
     */
    public static final int CAPTCHA_INVALID = 40011;

    /**
     * 未登录、凭据无效或会话已失效
     */
    public static final int UNAUTHENTICATED = 40100;

    /**
     * 身份有效，但没有执行该操作的权限
     */
    public static final int FORBIDDEN = 40300;

    /**
     * CSRF 凭据缺失、无效或请求来源校验失败
     */
    public static final int CSRF_INVALID = 40301;

    /**
     * 目标资源不存在或已被删除
     */
    public static final int NOT_FOUND = 40400;

    /**
     * 唯一约束、资源关联或并发状态产生冲突
     */
    public static final int CONFLICT = 40900;

    /**
     * 操作超过账号或 IP 的频率限制
     */
    public static final int RATE_LIMITED = 42900;

    /**
     * Redis 等必要依赖暂不可用
     */
    public static final int DEPENDENCY_UNAVAILABLE = 50300;

    /**
     * 未预期的服务器内部错误；对客户端隐藏实现细节
     */
    public static final int INTERNAL_ERROR = 50000;

    private ResultCodes() {}
}
