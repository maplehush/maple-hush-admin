package com.maplehush.admin.config.docs;

import com.maplehush.admin.system.docs.config.ApiDocumentationAccess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 服务启动成功后打印本地文档地址；使用实际监听端口及上下文路径。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiDocumentationStartupLogger {
    private static final Logger LOG = LoggerFactory.getLogger(ApiDocumentationStartupLogger.class);

    /**
     * 只有成功启动的 Servlet 应用才输出地址，关闭文档时不输出不可用链接。
     *
     * @param event 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        if (!(event.getApplicationContext() instanceof ServletWebServerApplicationContext context)) {
            return;
        }
        Environment environment = context.getEnvironment();
        if (!ApiDocumentationAccess.enabled(environment)) {
            LOG.info("""

                    ============================================================
                      Maple Hush Admin started successfully!
                    ------------------------------------------------------------
                      接口文档已关闭
                    ============================================================
                    """);
            return;
        }
        int port = context.getWebServer().getPort();
        String contextPath = context.getServletContext() == null
                ? "" : context.getServletContext().getContextPath();
        String baseUrl = UriComponentsBuilder.newInstance()
                .scheme(environment.getProperty("server.ssl.enabled", Boolean.class, false) ? "https" : "http")
                .host("localhost").port(port).path(contextPath).build().toUriString();
        LOG.info("""

                ============================================================
                  Maple Hush Admin started successfully!
                ------------------------------------------------------------
                  Knife4j: {}/doc.html
                  OpenAPI: {}/v3/api-docs
                ============================================================
                """, baseUrl, baseUrl);
    }
}
