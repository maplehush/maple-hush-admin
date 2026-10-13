package com.maplehush.admin.common.constant;

/** 全局稳定响应码。 */
public final class ResultCodes {
    public static final int SUCCESS = 0;
    public static final int BAD_REQUEST = 40000;
    public static final int INVALID_ARGUMENT = 40010;
    public static final int CAPTCHA_INVALID = 40011;
    public static final int UNAUTHENTICATED = 40100;
    public static final int FORBIDDEN = 40300;
    public static final int NOT_FOUND = 40400;
    public static final int CONFLICT = 40900;
    public static final int RATE_LIMITED = 42900;
    public static final int DEPENDENCY_UNAVAILABLE = 50300;
    public static final int INTERNAL_ERROR = 50000;

    private ResultCodes() {
    }
}
