package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * 每次创建并清理隔离 schema，验证首次引导与离线恢复的启动门禁
 */
@EnabledIfEnvironmentVariable(named = "T04_IT_ENABLED", matches = "true")
class BootstrapGuardIT {
    private String schema;

    @Test
    void bootstrapIsSingleUseAndRecoveryOnlyChangesOriginalHolder() throws Exception {
        schema = "maple_hush_t04_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Path directory = Files.createTempDirectory("maple-hush-recovery-");
        Path passwordFile = directory.resolve("password.txt");
        try (Connection server =
                DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/", "root", "123456")) {
            try (Statement statement = server.createStatement()) {
                statement.execute("CREATE DATABASE " + schema);
            }
            try {
                assertThrows(Exception.class, () -> start(false, false, ""));
                try (ConfigurableApplicationContext ignored = start(true, false, "")) {
                    assertEquals(1, scalar(server, "SELECT COUNT(*) FROM " + schema + ".sys_user"));
                    assertEquals(
                            1, scalar(server, "SELECT COUNT(*) FROM " + schema + ".sys_user_role"));
                    assertEquals(
                            1,
                            scalar(
                                    server,
                                    "SELECT must_change_password FROM " + schema + ".sys_user"));
                    String hash = text(server, "SELECT password FROM " + schema + ".sys_user");
                    assertFalse("12345678".equals(hash));
                    assertTrue(new BCryptPasswordEncoder().matches("12345678", hash));
                }
                try (ConfigurableApplicationContext ignored = start(true, false, "")) {
                    assertEquals(1, scalar(server, "SELECT COUNT(*) FROM " + schema + ".sys_user"));
                }
                try (Statement statement = server.createStatement()) {
                    statement.execute("DELETE FROM " + schema + ".sys_user_role");
                }
                assertThrows(Exception.class, () -> start(false, false, ""));
                try (Statement statement = server.createStatement()) {
                    statement.execute(
                            "INSERT INTO "
                                    + schema
                                    + ".sys_user_role (user_id,role_id) "
                                    + "SELECT u.id,r.id FROM "
                                    + schema
                                    + ".sys_user u,"
                                    + schema
                                    + ".sys_role r "
                                    + "WHERE u.username='admin' AND r.role_code='super_admin'");
                }
                try (Statement statement = server.createStatement()) {
                    statement.execute(
                            "INSERT INTO "
                                    + schema
                                    + ".sys_user "
                                    + "(username,real_name,password,status,must_change_password,auth_version) "
                                    + "VALUES ('second','Second','hash',1,1,0)");
                    statement.execute(
                            "INSERT INTO "
                                    + schema
                                    + ".sys_user_role (user_id,role_id) "
                                    + "SELECT u.id,r.id FROM "
                                    + schema
                                    + ".sys_user u,"
                                    + schema
                                    + ".sys_role r "
                                    + "WHERE u.username='second' AND r.role_code='super_admin'");
                }
                assertThrows(Exception.class, () -> start(false, false, ""));
                try (Statement statement = server.createStatement()) {
                    statement.execute(
                            "DELETE FROM "
                                    + schema
                                    + ".sys_user_role WHERE user_id="
                                    + "(SELECT id FROM "
                                    + schema
                                    + ".sys_user WHERE username='second')");
                    statement.execute(
                            "DELETE FROM " + schema + ".sys_user WHERE username='second'");
                    statement.execute(
                            "UPDATE " + schema + ".sys_user SET status=0 WHERE username='admin'");
                    statement.execute(
                            "INSERT INTO "
                                    + schema
                                    + ".auth_refresh_session "
                                    + "(user_id,family_id,token_hash,expire_time) SELECT id,'12345678-1234-1234-1234-123456789012',"
                                    + "REPEAT('a',64),DATE_ADD(NOW(),INTERVAL 1 DAY) FROM "
                                    + schema
                                    + ".sys_user");
                }
                assertThrows(Exception.class, () -> start(false, false, ""));
                try (ConfigurableApplicationContext ignored =
                        start(false, true, passwordFile.toString())) {
                    assertTrue(Files.exists(passwordFile));
                }
                String recovered = Files.readString(passwordFile).trim();
                assertEquals(32, recovered.length());
                assertTrue(
                        new BCryptPasswordEncoder()
                                .matches(
                                        recovered,
                                        text(
                                                server,
                                                "SELECT password FROM " + schema + ".sys_user")));
                assertEquals(1, scalar(server, "SELECT status FROM " + schema + ".sys_user"));
                assertEquals(
                        1,
                        scalar(server, "SELECT must_change_password FROM " + schema + ".sys_user"));
                assertEquals(1, scalar(server, "SELECT auth_version FROM " + schema + ".sys_user"));
                assertEquals(
                        1, scalar(server, "SELECT COUNT(*) FROM " + schema + ".sys_user_role"));
                assertEquals(
                        1,
                        scalar(
                                server,
                                "SELECT COUNT(*) FROM "
                                        + schema
                                        + ".auth_refresh_session WHERE revoked_time IS NOT NULL"));
                assertEquals(
                        1,
                        scalar(
                                server,
                                "SELECT COUNT(*) FROM "
                                        + schema
                                        + ".sys_operation_log WHERE operation_code='auth:super-admin-recovery'"));
            } finally {
                try (Statement statement = server.createStatement()) {
                    statement.execute("DROP DATABASE IF EXISTS " + schema);
                }
            }
        } finally {
            Files.deleteIfExists(passwordFile);
            Files.deleteIfExists(directory);
        }
    }

    private ConfigurableApplicationContext start(
            boolean bootstrap, boolean recover, String output) {
        return new SpringApplicationBuilder(AdminApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.profiles.active=test",
                        "--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/"
                                + schema
                                + "?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true",
                        "--maple-hush.auth.bootstrap-enabled=" + bootstrap,
                        "--maple-hush.maintenance.recover-super-admin=" + recover,
                        "--maple-hush.maintenance.recovery-output=" + output);
    }

    private static int scalar(Connection connection, String query) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(query)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static String text(Connection connection, String query) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(query)) {
            rows.next();
            return rows.getString(1);
        }
    }
}
