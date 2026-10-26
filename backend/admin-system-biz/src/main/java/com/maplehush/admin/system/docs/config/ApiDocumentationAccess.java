package com.maplehush.admin.system.docs.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * 文档在开发、测试环境开放；任何混合生产环境均关闭。
 */
public final class ApiDocumentationAccess {
    /**
     * 开发文档入口、定义文件及 UI 静态资源，均不包含业务 API。
     */
    public static final String[] PATHS = {
        "/doc.html", "/v3/api-docs", "/v3/api-docs/**",
        "/swagger-ui.html", "/swagger-ui/**", "/webjars/**"
    };

    private ApiDocumentationAccess() {}

    /**
     * 同时检查环境与生成器开关，避免误用生产配置暴露文档。
     *
     * @param environment 当前运行环境
     * @return 是否允许访问文档资源
     */
    public static boolean enabled(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("(dev | test) & !prod"))
                && environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false);
    }
}
