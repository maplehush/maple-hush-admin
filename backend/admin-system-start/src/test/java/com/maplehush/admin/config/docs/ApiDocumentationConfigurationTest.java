package com.maplehush.admin.config.docs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证通用错误集中说明，并区分匿名、Bearer 与 Cookie 调试流程。
 */
class ApiDocumentationConfigurationTest {
    @Test
    void publicErrorSchemaIsRestoredAfterSpringdocSchemaPruning() {
        ApiDocumentationConfiguration configuration = new ApiDocumentationConfiguration();
        OpenAPI api = configuration.platformOpenApi().components(new Components()).paths(new Paths());
        Operation csrf = new Operation().responses(new ApiResponses());
        Operation refresh = new Operation().responses(new ApiResponses());
        api.path("/api/auth/csrf", new PathItem().get(csrf));
        api.path("/api/auth/refresh", new PathItem().post(refresh));
        configuration.platformDocumentation().customise(api);
        assertNotNull(api.getComponents().getSchemas().get("ApiError"));
        assertTrue(api.getInfo().getDescription().contains("## 通用错误响应"));
        for (com.maplehush.admin.common.exception.ErrorCode error :
                com.maplehush.admin.common.exception.ErrorCode.values()) {
            assertTrue(api.getInfo().getDescription().contains(
                    "| " + error.code() + " | " + error.value() + " | " + error.status().value() + " |"));
        }
        assertTrue(csrf.getResponses().isEmpty());
        assertEquals(java.util.Set.of("200"), refresh.getResponses().keySet());
        assertTrue(csrf.getSecurity().isEmpty());
        assertTrue(refresh.getSecurity().getFirst().containsKey("RefreshCookie"));
        assertTrue(refresh.getParameters().stream().anyMatch(parameter ->
                parameter.getName().equals("X-XSRF-TOKEN") && parameter.getRequired()));
    }
}
