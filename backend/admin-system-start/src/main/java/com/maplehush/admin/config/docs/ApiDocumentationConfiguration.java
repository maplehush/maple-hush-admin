package com.maplehush.admin.config.docs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;

/**
 * 开发、测试接口文档及调试约定；不改变运行时认证、权限或 CSRF 行为。
 */
@Configuration(proxyBeanMethods = false)
@Profile("(dev | test) & !prod")
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class ApiDocumentationConfiguration {
    @Bean
    OpenAPI platformOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Maple Hush Admin 接口文档").version("1.0")
                        .description("开发环境调试入口。先获取验证码并登录，再将 accessToken 填入 BearerAuth。"
                                + "refresh/logout 使用 HttpOnly Cookie，先调用 csrf 获取 data.token，"
                                + "填写 X-XSRF-TOKEN。首次改密使用 passwordChangeToken。"
                                + "统一响应：code=0 表示成功；错误见 HTTP 状态、error 和 message。"
                                + "时间统一为 Asia/Shanghai；ID 使用十进制字符串。\n\n"
                                + """
                                ## 通用错误响应

                                错误响应统一包含 `code`、`data`（为空）、`error` 和 `message`。
                                以下为全局约定，具体是否发生取决于接口和请求条件。

                                | code | error | HTTP 状态 | 含义 |
                                | --- | --- | --- | --- |
                                | 0 | 空 | 200 | 业务处理成功 |
                                | 40000 | BAD_REQUEST | 400 | 请求格式错误，如 JSON 无法解析、参数类型错误 |
                                | 40010 | INVALID_ARGUMENT | 400 | 请求参数不符合校验规则 |
                                | 40011 | CAPTCHA_INVALID | 400 | 验证码错误、过期或已使用；需重新获取 |
                                | 40100 | UNAUTHENTICATED | 401 | 未登录、令牌过期或无效、会话失效；登录时也用于账号或密码错误 |
                                | 40300 | FORBIDDEN | 403 | 身份有效，但没有操作权限 |
                                | 40301 | CSRF_INVALID | 403 | CSRF 凭据缺失或无效，或请求来源校验失败 |
                                | 40400 | NOT_FOUND | 404 | 目标不存在或已删除 |
                                | 40900 | CONFLICT | 409 | 唯一约束、资源关联或并发状态冲突 |
                                | 42900 | RATE_LIMITED | 429 | 请求过于频繁，需稍后重试 |
                                | 50000 | INTERNAL_ERROR | 500 | 服务器内部异常 |
                                | 50300 | DEPENDENCY_UNAVAILABLE | 503 | Redis 等必要依赖暂不可用 |

                                """))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", bearer("普通 accessToken，不含 Bearer 前缀"))
                        .addSecuritySchemes("InitialPassword", bearer("登录返回的 passwordChangeToken"))
                        .addSecuritySchemes("RefreshCookie", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE)
                                .name("mh_refresh").description("登录时由浏览器保存的 HttpOnly Cookie，无需手工输入"))
                        .addSchemas("ApiError", errorSchema()))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
    }

    @Bean
    OpenApiCustomizer platformDocumentation() {
        return api -> {
            api.getComponents().addSchemas("ApiError", errorSchema());
            api.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
            if (List.of("/api/auth/captcha", "/api/auth/login", "/api/auth/csrf").contains(path)) {
                operation.setSecurity(List.of());
            } else if (path.equals("/api/auth/initial-password")) {
                operation.setSecurity(List.of(new SecurityRequirement().addList("InitialPassword")));
            } else if (path.equals("/api/auth/refresh") || path.equals("/api/auth/logout")) {
                operation.setSecurity(List.of(new SecurityRequirement().addList("RefreshCookie")));
                operation.addParametersItem(new Parameter().name("X-XSRF-TOKEN").in("header")
                        .required(true).description("先调用 GET /api/auth/csrf，复制响应 data.token；不要复制 HttpOnly Cookie 值")
                        .schema(new StringSchema()));
            }
            if (path.equals("/api/auth/login")) {
                operation.getResponses().addApiResponse("200", new ApiResponse()
                        .description("成功返回 accessToken；首次登录返回 passwordChangeRequired=true 和 passwordChangeToken。Cookie 自动保存。")
                        .content(loginExample()));
            }
            if (path.equals("/api/files/upload") || path.equals("/api/user/avatar")) {
                ObjectSchema upload = new ObjectSchema();
                upload.addProperty("file", new StringSchema().format("binary").description("单个 PNG/JPEG/WebP 图片，不超过 2 MiB；不接受其他字段"));
                upload.required(List.of("file"));
                operation.setRequestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                        .required(true).content(new Content().addMediaType("multipart/form-data",
                                new io.swagger.v3.oas.models.media.MediaType().schema(upload))));
            }
            if (path.equals("/api/auth/refresh")) {
                operation.getResponses().addApiResponse("200", new ApiResponse()
                        .description("轮换 HttpOnly refresh Cookie；响应为 JSON 字符串 accessToken，不使用 Result 包装；重放会使会话族失效")
                        .content(json(new StringSchema().example("轮换后签发的访问令牌"))));
            }
        }));
        };
    }

    private static Schema<?> errorSchema() {
        return new ObjectSchema()
                .addProperty("code", new IntegerSchema().description("非零稳定业务错误码").example(40100))
                .addProperty("data", new Schema<>().description("错误时为空"))
                .addProperty("error", new StringSchema().example("UNAUTHENTICATED"))
                .addProperty("message", new StringSchema().example("请重新登录"));
    }

    private static Content loginExample() {
        Content content = json(new ObjectSchema());
        content.get("application/json").example(Map.of("code", 0,
                "data", Map.of("passwordChangeRequired", false, "accessToken", "登录签发的访问令牌"), "message", "ok"));
        return content;
    }

    private static SecurityScheme bearer(String description) {
        return new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                .bearerFormat("JWT").description(description);
    }

    private static Content json(Schema<?> schema) {
        return new Content().addMediaType("application/json", new io.swagger.v3.oas.models.media.MediaType().schema(schema));
    }
}
