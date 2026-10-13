package com.maplehush.admin.common.constant;

/** 全局稳定响应码。 */
public final class ResultCodes {
    public static final int SUCCESS = 0;
    public static final int BAD_REQUEST = 40000;
    public static final int NOT_FOUND = 40400;
    public static final int CONFLICT = 40900;
    public static final int INTERNAL_ERROR = 50000;

    private ResultCodes() {
    }
}
