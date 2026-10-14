package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.service.IAuthSessionService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * 真实 HTTP/MySQL/Redis 验证字典唯一性、默认项并发与独立审计
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "maple-hush.auth.bootstrap-enabled=true")
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "T11_IT_ENABLED", matches = "true")
class DictManagementIT {
    private static final String SCHEMA =
            "maple_hush_t11_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                "jdbc:mysql://127.0.0.1:3306/", "root", "123456");
                var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE " + SCHEMA);
        }
        properties.add(
                "spring.datasource.url",
                () ->
                        "jdbc:mysql://127.0.0.1:3306/"
                                + SCHEMA
                                + "?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true");
        properties.add("maple-hush.redis.key-prefix", () -> SCHEMA);
    }

    @AfterAll
    static void dropSchema() throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                "jdbc:mysql://127.0.0.1:3306/", "root", "123456");
                var sql = connection.createStatement()) {
            sql.execute("DROP DATABASE IF EXISTS " + SCHEMA);
        }
    }

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IAuthSessionService sessions;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JsonMapper json;
    @Autowired private RedisKeyBuilder keys;

    @Autowired
    @Qualifier("stringValueRedisTemplate")
    private RedisTemplate<String, String> redis;

    private final HttpClient client = HttpClient.newHttpClient();
    private long admin;
    private long builtin;
    private String access;

    @BeforeEach
    void prepare() {
        admin = jdbc.queryForObject("SELECT id FROM sys_user WHERE username='admin'", Long.class);
        builtin =
                jdbc.queryForObject(
                        "SELECT id FROM sys_role WHERE role_code='super_admin'", Long.class);
        jdbc.update("UPDATE sys_user SET must_change_password=0 WHERE id=?", admin);
        access = sessions.open(admin, 0).accessToken();
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM sys_dict_item");
        jdbc.update("DELETE FROM sys_dict_type");
        jdbc.update("DELETE FROM auth_refresh_session");
        jdbc.update("DELETE FROM sys_login_log");
        jdbc.update("DELETE FROM sys_operation_log");
        jdbc.update("DELETE FROM sys_user_role WHERE role_id<>?", builtin);
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id<>?", builtin);
        jdbc.update("DELETE FROM sys_role WHERE id<>?", builtin);
        for (Long id : jdbc.queryForList("SELECT id FROM sys_user", Long.class)) {
            redis.delete(keys.key("user", id.toString()));
        }
        jdbc.update("DELETE FROM sys_user WHERE id<>?", admin);
    }

    @Test
    void typeCrudPagingStableCodeAndFiltersMatchContract() throws Exception {
        long one = type("state_one", "状态一", 1);
        long two = type("state_two", "状态二", 0);
        var response =
                request(
                        "GET",
                        "/api/system/dict/type/list?page=2&pageSize=1&dictCode=state&sortBy=id&sortOrder=asc",
                        null,
                        access);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(2, data(response).get("total").asInt());
        assertEquals(Long.toString(two), data(response).get("items").get(0).get("id").asString());
        var detail = data(request("GET", "/api/system/dict/type/" + one, null, access));
        assertEquals("状态一", detail.get("name").asString());
        assertEquals("state_one", detail.get("dictCode").asString());
        assertEquals(
                1,
                data(request("GET", "/api/system/dict/type/list?status=0", null, access))
                        .get("total")
                        .asInt());
        assertEquals(
                409,
                request(
                                "POST",
                                "/api/system/dict/type",
                                Map.of("dictCode", "state_one", "name", "重复"),
                                access)
                        .statusCode());
        assertEquals(
                409,
                request(
                                "PUT",
                                "/api/system/dict/type/" + one,
                                Map.of("dictCode", "changed", "name", "改码"),
                                access)
                        .statusCode());
        assertEquals(
                200,
                request("PUT", "/api/system/dict/type/" + two, Map.of("name", "停用仍保留"), access)
                        .statusCode());
        assertEquals(
                0,
                data(request("GET", "/api/system/dict/type/" + two, null, access))
                        .get("status")
                        .asInt());
        assertEquals(
                200, request("DELETE", "/api/system/dict/type/" + one, null, access).statusCode());
        assertEquals(
                404, request("GET", "/api/system/dict/type/" + one, null, access).statusCode());
    }

    @Test
    void itemCrudColorNormalizationUniquenessAndStableOrdering() throws Exception {
        long type = type("colors", "颜色", 1);
        long other = type("other", "其他", 1);
        long first = item(type, "红色", "same", "#FFAA00", false, 0, 20);
        long second = item(type, "绿色", "two", null, false, 1, 10);
        assertEquals(
                "#ffaa00",
                data(request("GET", "/api/system/dict/item/" + first, null, access))
                        .get("color")
                        .asString());
        assertEquals(
                409,
                request(
                                "POST",
                                "/api/system/dict/item",
                                itemForm(type, "重复", "same", null, false, 1, 0),
                                access)
                        .statusCode());
        item(other, "其他类型同值", "same", null, false, 1, 0);
        var list =
                data(request("GET", "/api/system/dict/item/list?dictTypeId=" + type, null, access));
        assertEquals(2, list.get("total").asInt());
        assertEquals(Long.toString(second), list.get("items").get(0).get("id").asString());
        assertEquals(
                200,
                request(
                                "PUT",
                                "/api/system/dict/item/" + first,
                                itemForm(type, "改标签", "same", "", null, null, null),
                                access)
                        .statusCode());
        var detail = data(request("GET", "/api/system/dict/item/" + first, null, access));
        assertTrue(detail.get("color").isNull());
        assertEquals(0, detail.get("status").asInt());
        assertEquals(20, detail.get("sort").asInt());
        assertEquals(
                409,
                request(
                                "PUT",
                                "/api/system/dict/item/" + first,
                                itemForm(other, "移动", "same", null, false, 1, 0),
                                access)
                        .statusCode());
        assertEquals(
                200,
                request("DELETE", "/api/system/dict/item/" + first, null, access).statusCode());
        assertEquals(
                404, request("GET", "/api/system/dict/item/" + first, null, access).statusCode());
    }

    @Test
    void authenticatedLookupOnlyReturnsEnabledOptionsWithColor() throws Exception {
        long type = type("lookup", "展示", 1);
        item(type, "启用", "yes", "#52C41A", true, 1, 0);
        item(type, "停用", "no", "#FF0000", false, 0, 1);
        String token = ordinaryToken(false);
        var response = request("GET", "/api/system/dict/type/lookup/items", null, token);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(1, data(response).size());
        assertEquals("#52c41a", data(response).get(0).get("color").asString());
        assertEquals("yes", data(response).get(0).get("value").asString());
        assertTrue(data(response).get(0).get("defaultItem").asBoolean());
        assertFalse(data(response).get(0).has("remark"));
        assertEquals(403, request("GET", "/api/system/dict/type/list", null, token).statusCode());
        assertEquals(
                200,
                request(
                                "PUT",
                                "/api/system/dict/type/" + type,
                                Map.of("name", "展示", "status", 0),
                                access)
                        .statusCode());
        assertTrue(
                data(request("GET", "/api/system/dict/type/lookup/items", null, token)).isEmpty());
        assertEquals(
                404,
                request("GET", "/api/system/dict/type/missing/items", null, token).statusCode());
        assertEquals(
                401, request("GET", "/api/system/dict/type/lookup/items", null, null).statusCode());
    }

    @Test
    void defaultSwitchIsAtomicAndIndependentFromStatusAndColor() throws Exception {
        long type = type("defaults", "默认", 1);
        long one = item(type, "一", "one", null, true, 1, 0);
        long two = item(type, "二", "two", "#ABCDEF", true, 0, 1);
        assertEquals(0, scalar("SELECT is_default FROM sys_dict_item WHERE id=?", one));
        assertEquals(1, scalar("SELECT is_default FROM sys_dict_item WHERE id=?", two));
        assertEquals(0, scalar("SELECT status FROM sys_dict_item WHERE id=?", two));
        assertEquals(
                200,
                request(
                                "PUT",
                                "/api/system/dict/item/" + one,
                                itemForm(type, "一", "one", null, true, null, null),
                                access)
                        .statusCode());
        assertEquals(1, defaults(type));
        assertEquals(0, scalar("SELECT is_default FROM sys_dict_item WHERE id=?", two));
        assertEquals(
                200, request("DELETE", "/api/system/dict/item/" + one, null, access).statusCode());
        assertEquals(0, defaults(type));
        assertEquals(200, request("GET", "/api/auth/codes", null, access).statusCode());
    }

    @Test
    void duplicateValueRollbackRestoresOldDefaultAndWritesFailureAudit() throws Exception {
        long type = type("rollback", "回滚", 1);
        long one = item(type, "原默认", "one", null, true, 1, 0);
        long two = item(type, "原二", "two", null, false, 1, 1);
        assertEquals(
                409,
                request(
                                "PUT",
                                "/api/system/dict/item/" + two,
                                itemForm(type, "应回滚", "one", "#FFFFFF", true, 1, 0),
                                access)
                        .statusCode());
        assertEquals(1, scalar("SELECT is_default FROM sys_dict_item WHERE id=?", one));
        assertEquals(0, scalar("SELECT is_default FROM sys_dict_item WHERE id=?", two));
        assertEquals(
                "two",
                jdbc.queryForObject(
                        "SELECT value FROM sys_dict_item WHERE id=?", String.class, two));
        assertEquals(
                1,
                scalar(
                        "SELECT COUNT(*) FROM sys_operation_log WHERE operation_code='system:dict:update' AND success=0 AND target_id=?",
                        Long.toString(two)));
    }

    @Test
    void concurrentUpdatesAndCreatesKeepAtMostOneDefault() throws Exception {
        long type = type("concurrent", "并发", 1);
        long one = item(type, "一", "one", null, false, 1, 0);
        long two = item(type, "二", "two", null, false, 1, 0);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first =
                    executor.submit(
                            () -> {
                                go.await();
                                return request(
                                                "PUT",
                                                "/api/system/dict/item/" + one,
                                                itemForm(type, "一", "one", null, true, 1, 0),
                                                access)
                                        .statusCode();
                            });
            var second =
                    executor.submit(
                            () -> {
                                go.await();
                                return request(
                                                "PUT",
                                                "/api/system/dict/item/" + two,
                                                itemForm(type, "二", "two", null, true, 1, 0),
                                                access)
                                        .statusCode();
                            });
            go.countDown();
            assertEquals(200, first.get());
            assertEquals(200, second.get());
        }
        assertEquals(1, defaults(type));
        CountDownLatch createGo = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first =
                    executor.submit(
                            () -> {
                                createGo.await();
                                return request(
                                                "POST",
                                                "/api/system/dict/item",
                                                itemForm(type, "三", "three", null, true, 1, 0),
                                                access)
                                        .statusCode();
                            });
            var second =
                    executor.submit(
                            () -> {
                                createGo.await();
                                return request(
                                                "POST",
                                                "/api/system/dict/item",
                                                itemForm(type, "四", "four", null, true, 1, 0),
                                                access)
                                        .statusCode();
                            });
            createGo.countDown();
            assertEquals(200, first.get());
            assertEquals(200, second.get());
        }
        assertEquals(1, defaults(type));
        assertEquals(4, scalar("SELECT COUNT(*) FROM sys_dict_item WHERE dict_type_id=?", type));
    }

    @Test
    void deletingTypeWithAnyItemIsRejectedUntilItemsAreDeleted() throws Exception {
        long type = type("delete_guard", "删除", 1);
        long item = item(type, "停用项", "x", null, false, 0, 0);
        assertEquals(
                409, request("DELETE", "/api/system/dict/type/" + type, null, access).statusCode());
        assertEquals(
                200, request("DELETE", "/api/system/dict/item/" + item, null, access).statusCode());
        assertEquals(
                200, request("DELETE", "/api/system/dict/type/" + type, null, access).statusCode());
        assertEquals(
                2,
                scalar(
                        "SELECT COUNT(*) FROM sys_operation_log WHERE operation_code='system:dict:delete' AND success=1"));
    }

    @Test
    void concurrentTypeDeleteAndItemCreateCannotLeaveOrphans() throws Exception {
        long type = type("delete_race", "删除并发", 1);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletion =
                    executor.submit(
                            () -> {
                                go.await();
                                return request(
                                                "DELETE",
                                                "/api/system/dict/type/" + type,
                                                null,
                                                access)
                                        .statusCode();
                            });
            var creation =
                    executor.submit(
                            () -> {
                                go.await();
                                return request(
                                                "POST",
                                                "/api/system/dict/item",
                                                itemForm(type, "项", "v", null, true, 1, 0),
                                                access)
                                        .statusCode();
                            });
            go.countDown();
            int deleted = deletion.get();
            int created = creation.get();
            assertTrue(deleted == 200 && created == 404 || deleted == 409 && created == 200);
        }
        assertEquals(
                0,
                scalar(
                        "SELECT COUNT(*) FROM sys_dict_item i LEFT JOIN sys_dict_type t ON t.id=i.dict_type_id WHERE t.id IS NULL"));
    }

    @Test
    void readPermissionDoesNotAllowWritesAndEachEndpointIsGuarded() throws Exception {
        long type = type("rights", "权限", 1);
        long item = item(type, "项", "v", null, false, 1, 0);
        String token = ordinaryToken(true);
        assertEquals(200, request("GET", "/api/system/dict/type/list", null, token).statusCode());
        assertEquals(
                200, request("GET", "/api/system/dict/item/" + item, null, token).statusCode());
        assertEquals(
                403,
                request(
                                "POST",
                                "/api/system/dict/type",
                                Map.of("dictCode", "denied", "name", "拒绝"),
                                token)
                        .statusCode());
        assertEquals(
                403,
                request("PUT", "/api/system/dict/type/" + type, Map.of("name", "拒绝"), token)
                        .statusCode());
        assertEquals(
                403, request("DELETE", "/api/system/dict/type/" + type, null, token).statusCode());
        assertEquals(
                403,
                request(
                                "POST",
                                "/api/system/dict/item",
                                itemForm(type, "拒绝", "new", null, false, 1, 0),
                                token)
                        .statusCode());
        assertEquals(
                403,
                request(
                                "PUT",
                                "/api/system/dict/item/" + item,
                                itemForm(type, "拒绝", "v", null, false, 1, 0),
                                token)
                        .statusCode());
        assertEquals(
                403, request("DELETE", "/api/system/dict/item/" + item, null, token).statusCode());
    }

    @Test
    void invalidFieldsColorsPaginationAndSortAreRejected() throws Exception {
        long type = type("validation", "校验", 1);
        for (String color : List.of("red", "#123", "#12345678", "#GGGGGG", " #ffffff")) {
            assertEquals(
                    400,
                    request(
                                    "POST",
                                    "/api/system/dict/item",
                                    itemForm(type, "颜色", "v", color, false, 1, 0),
                                    access)
                            .statusCode());
        }
        assertEquals(
                400,
                request(
                                "POST",
                                "/api/system/dict/type",
                                Map.of("dictCode", "bad code", "name", "x"),
                                access)
                        .statusCode());
        assertEquals(
                400,
                request(
                                "POST",
                                "/api/system/dict/type",
                                Map.of("dictCode", "extra", "name", "x", "sql", "bad"),
                                access)
                        .statusCode());
        assertEquals(
                400,
                request("GET", "/api/system/dict/type/list?pageSize=101", null, access)
                        .statusCode());
        assertEquals(
                400,
                request(
                                "GET",
                                "/api/system/dict/item/list?dictTypeId="
                                        + type
                                        + "&sortBy=password",
                                null,
                                access)
                        .statusCode());
        assertEquals(
                400,
                request("GET", "/api/system/dict/type/list?sortOrder=invalid", null, access)
                        .statusCode());
        assertEquals(400, request("GET", "/api/system/dict/item/-1", null, access).statusCode());
        assertEquals(
                404,
                request(
                                "POST",
                                "/api/system/dict/item",
                                itemForm(9223372036854775807L, "缺失", "v", null, false, 1, 0),
                                access)
                        .statusCode());
        assertEquals(401, request("GET", "/api/system/dict/type/list", null, null).statusCode());
    }

    private long type(String code, String name, int status) throws Exception {
        var response =
                request(
                        "POST",
                        "/api/system/dict/type",
                        Map.of("dictCode", code, "name", name, "status", status),
                        access);
        assertEquals(200, response.statusCode(), response.body());
        return Long.parseLong(data(response).get("id").asString());
    }

    private long item(
            long type,
            String label,
            String value,
            String color,
            boolean selected,
            int status,
            int sort)
            throws Exception {
        var response =
                request(
                        "POST",
                        "/api/system/dict/item",
                        itemForm(type, label, value, color, selected, status, sort),
                        access);
        assertEquals(200, response.statusCode(), response.body());
        return Long.parseLong(data(response).get("id").asString());
    }

    private Map<String, Object> itemForm(
            long type,
            String label,
            String value,
            String color,
            Boolean selected,
            Integer status,
            Integer sort) {
        var form = new HashMap<String, Object>();
        form.put("dictTypeId", Long.toString(type));
        form.put("label", label);
        form.put("value", value);
        form.put("color", color);
        form.put("defaultItem", selected);
        form.put("status", status);
        form.put("sort", sort);
        form.put("remark", null);
        return form;
    }

    private int scalar(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private int defaults(long type) {
        return scalar(
                "SELECT COUNT(*) FROM sys_dict_item WHERE dict_type_id=? AND is_default=1", type);
    }

    private String ordinaryToken(boolean reader) {
        String name = "dict_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
                "INSERT INTO sys_user(username,real_name,password,status,must_change_password) VALUES (?, '字典测试',?,1,0)",
                name,
                passwords.encode("dictionary strong phrase"));
        long user =
                jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, name);
        if (reader) {
            jdbc.update(
                    "INSERT INTO sys_role(role_code,role_name,is_builtin,status) VALUES (?, '字典查看',0,1)",
                    name);
            long role =
                    jdbc.queryForObject(
                            "SELECT id FROM sys_role WHERE role_code=?", Long.class, name);
            jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", user, role);
            jdbc.update(
                    "INSERT INTO sys_role_menu(role_id,menu_id) VALUES (?,1),(?,50)", role, role);
        }
        return sessions.open(user, 0).accessToken();
    }

    private JsonNode data(HttpResponse<String> response) {
        return json.readTree(response.body()).get("data");
    }

    private HttpResponse<String> request(String method, String path, Object body, String token)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return client.send(
                builder.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(
                                                json.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
