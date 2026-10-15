package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

/**
 * 在独立 MySQL 库验证日志菜单的全新安装和带授权数据的升级，不修改开发库。
 */
@EnabledIfEnvironmentVariable(named = "LOG_MENU_IT_ENABLED", matches = "true")
class LogMenuMigrationIT {
    private final String schema =
            "maple_hush_logmenu_" + UUID.randomUUID().toString().replace("-", "");
    private final JdbcTemplate server =
            new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/", "root", "123456"));
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createSchema() {
        server.execute("CREATE DATABASE " + schema);
        source = new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:3306/" + schema
                        + "?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true",
                "root", "123456");
        jdbc = new JdbcTemplate(source);
    }

    @AfterEach
    void removeSchema() {
        server.execute("DROP DATABASE " + schema);
    }

    @Test
    void freshInstallationHasSeparateLogCatalog() {
        Flyway.configure().dataSource(source).load().migrate();
        assertEquals(4, count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1"));
        assertEquals(27, count("SELECT COUNT(*) FROM sys_menu"));
        assertEquals(27, count("SELECT COUNT(*) FROM sys_role_menu"));
        assertEquals(2, count("SELECT COUNT(*) FROM sys_menu WHERE parent_id IS NULL"));
        assertEquals(2, count("SELECT COUNT(*) FROM sys_menu m JOIN sys_menu p ON m.parent_id=p.id "
                + "WHERE p.name='LogManagement' AND m.auth_code IN ('system:login-log:list','system:operation-log:list')"));
        assertEquals("/log", jdbc.queryForObject(
                "SELECT path FROM sys_menu WHERE name='LogManagement'", String.class));
    }

    @Test
    void upgradePreservesLogGrantsAndInvalidatesOnlyAffectedUsers() {
        Flyway.configure().dataSource(source).target("3").load().migrate();
        jdbc.update("INSERT INTO sys_role(id,role_code,role_name) VALUES "
                + "(2,'log_reader','登录日志'),(3,'operation_reader','操作日志'),(4,'user_reader','用户只读')");
        jdbc.update("INSERT INTO sys_user(id,username,real_name,password) VALUES "
                + "(1,'admin','管理员','test'),(2,'login','登录只读','test'),"
                + "(3,'operation','操作只读','test'),(4,'users','用户只读','test')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (1,1),(2,2),(3,3),(4,4)");
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES (2,1),(2,60),(3,1),(3,70),(4,1),(4,10)");
        // 原库已有自定义菜单，迁移不得假定新目录 ID 一定为 71。
        jdbc.update("INSERT INTO sys_menu(id,name,title,type,path,redirect,meta_json) VALUES "
                + "(71,'Custom','自定义','catalog','/custom','/system/login-log',"
                + "'{\"activePath\":\"/system/operation-log\"}')");
        var flyway = Flyway.configure().dataSource(source).load();
        flyway.migrate();
        assertEquals(3, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id "
                + "WHERE m.name='LogManagement'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=2 AND menu_id=60"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=2 AND menu_id=70"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=3 AND menu_id=70"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id "
                + "WHERE role_id=4 AND m.name='LogManagement'"));
        assertEquals(3, count("SELECT COUNT(*) FROM sys_user WHERE id IN (1,2,3) AND auth_version=1"));
        assertEquals(0, count("SELECT auth_version FROM sys_user WHERE id=4"));
        assertEquals("/log/login-log", jdbc.queryForObject("SELECT redirect FROM sys_menu WHERE id=71", String.class));
        assertEquals("/log/operation-log", jdbc.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(meta_json,'$.activePath')) FROM sys_menu WHERE id=71", String.class));
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
