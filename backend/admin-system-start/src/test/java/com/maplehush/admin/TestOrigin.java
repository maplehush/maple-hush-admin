package com.maplehush.admin;

/**
 * 集成测试从显式允许的公开 Origin 发起 Cookie 写请求；未配置时兼容本地 dev 测试。
 */
final class TestOrigin {
    private TestOrigin() {
    }

    static String allowed(int port) {
        String configured = System.getenv("AUTH_ALLOWED_ORIGINS");
        if (configured != null && !configured.isBlank()) {
            return configured.split(",", 2)[0].trim();
        }
        return "http://127.0.0.1:" + port;
    }
}
