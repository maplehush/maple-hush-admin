package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.maplehush.admin.system.auth.bootstrap.BootstrapGuard;
import com.maplehush.admin.system.dict.entity.SysDictItem;
import com.maplehush.admin.system.dict.entity.SysDictType;
import com.maplehush.admin.system.dict.mapper.SysDictItemMapper;
import com.maplehush.admin.system.dict.mapper.SysDictTypeMapper;
import com.maplehush.admin.system.role.entity.SysRole;
import com.maplehush.admin.system.role.mapper.SysRoleMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Against an isolated, empty MySQL schema; opt in with T03_IT_ENABLED=true.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "T03_IT_ENABLED", matches = "true")
class SystemMigrationIT {
    @MockitoBean private BootstrapGuard bootstrapGuard;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SysRoleMapper roles;
    @Autowired private SysDictTypeMapper dictTypes;
    @Autowired private SysDictItemMapper dictItems;
    @Autowired private JsonMapper json;

    @Test
    void migrationAndSeedAreComplete() {
        assertEquals(
                3,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",
                        Integer.class));
        assertEquals(
                11,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
                                + "AND table_name<>'flyway_schema_history'",
                        Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_type", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role", Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_role WHERE role_code='super_admin' AND is_builtin=1",
                        Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_menu WHERE type='catalog' AND auth_code IS NULL",
                        Integer.class));
        assertEquals(
                7,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_menu WHERE type='menu'", Integer.class));
        assertEquals(
                18,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_menu WHERE type='button'", Integer.class));
        assertEquals(26, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu", Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_menu m LEFT JOIN sys_role_menu rm ON rm.menu_id=m.id "
                                + "WHERE rm.menu_id IS NULL",
                        Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_menu WHERE type='button' AND (path IS NOT NULL OR auth_code IS NULL)",
                        Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_menu WHERE type='menu' AND "
                                + "(component NOT LIKE '/system/%/list' OR auth_code IS NULL)",
                        Integer.class));
    }

    @Test
    @Transactional
    void mapperBooleanAndTimestampRoundTrip() throws Exception {
        assertEquals("+08:00", jdbc.queryForObject("SELECT @@session.time_zone", String.class));
        SysRole builtIn =
                roles.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysRole>()
                                .eq("role_code", "super_admin"));
        assertTrue(builtIn.getBuiltIn());
        assertNotNull(builtIn.getCreateTime());

        SysRole role = new SysRole();
        role.setRoleCode("t03_mapper_role");
        role.setRoleName("T03 测试角色");
        role.setBuiltIn(false);
        role.setStatus(1);
        assertEquals(1, roles.insert(role));
        assertNotNull(role.getId());
        assertFalse(roles.selectById(role.getId()).getBuiltIn());
        assertEquals(role.getCreateTime(), roles.selectById(role.getId()).getCreateTime());
        assertEquals(role.getUpdateTime(), roles.selectById(role.getId()).getUpdateTime());
        assertTrue(
                json.writeValueAsString(role)
                        .contains(
                                role.getCreateTime()
                                        .format(
                                                java.time.format.DateTimeFormatter.ofPattern(
                                                        "yyyy-MM-dd HH:mm:ss"))));

        // Wrapper-only update has no entity for MetaObjectHandler; set update_time explicitly.
        jdbc.update(
                "UPDATE sys_role SET update_time='2020-01-01 00:00:00' WHERE id=?", role.getId());
        assertEquals(
                1,
                roles.update(
                        null,
                        new UpdateWrapper<SysRole>()
                                .eq("id", role.getId())
                                .set("role_name", "T03 更新角色")
                                .set(
                                        "update_time",
                                        LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                                                .withNano(0))));
        assertTrue(
                roles.selectById(role.getId())
                        .getUpdateTime()
                        .isAfter(LocalDateTime.of(2020, 1, 1, 0, 0)));

        SysDictType type = new SysDictType();
        type.setDictCode("t03_type");
        type.setDictName("测试字典");
        type.setStatus(1);
        assertEquals(1, dictTypes.insert(type));
        SysDictItem item = new SysDictItem();
        item.setDictTypeId(type.getId());
        item.setLabel("默认项");
        item.setValue("yes");
        item.setSort(0);
        item.setDefaultItem(true);
        item.setStatus(1);
        assertEquals(1, dictItems.insert(item));
        assertTrue(dictItems.selectById(item.getId()).getDefaultItem());

        // Direct SQL uses the database default, with the same session zone and second precision.
        jdbc.update(
                "INSERT INTO sys_role (role_code, role_name) VALUES (?, ?)",
                "t03_sql_role",
                "SQL 角色");
        LocalDateTime sqlCreated =
                jdbc.queryForObject(
                        "SELECT create_time FROM sys_role WHERE role_code='t03_sql_role'",
                        LocalDateTime.class);
        long skew =
                Math.abs(
                        java.time.Duration.between(
                                        sqlCreated, LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
                                .getSeconds());
        assertTrue(skew < 10, "database default should use the application business time zone");
        assertEquals(0, sqlCreated.getNano());
        jdbc.update(
                "UPDATE sys_role SET update_time='2020-01-01 00:00:00' WHERE role_code='t03_sql_role'");
        jdbc.update("UPDATE sys_role SET role_name='SQL 角色更新' WHERE role_code='t03_sql_role'");
        LocalDateTime sqlUpdated =
                jdbc.queryForObject(
                        "SELECT update_time FROM sys_role WHERE role_code='t03_sql_role'",
                        LocalDateTime.class);
        assertTrue(sqlUpdated.isAfter(LocalDateTime.of(2020, 1, 1, 0, 0)));
    }

    @Test
    @Transactional
    void uniqueConstraintsAndUsefulIndexesExist() {
        List<String> indexes =
                jdbc.queryForList(
                        "SELECT DISTINCT index_name FROM information_schema.statistics "
                                + "WHERE table_schema=DATABASE()",
                        String.class);
        for (String name :
                List.of(
                        "uk_sys_user_username",
                        "uk_sys_role_code",
                        "uk_sys_menu_parent_path",
                        "uk_sys_menu_name",
                        "uk_sys_menu_auth_code",
                        "uk_sys_dict_item_type_value",
                        "uk_auth_refresh_token_hash",
                        "idx_auth_refresh_expire")) {
            assertTrue(indexes.contains(name), name);
        }
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "INSERT INTO sys_menu (name,title,type,path) VALUES ('T03Root','Root','catalog','/system')"));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "INSERT INTO sys_menu (parent_id,name,title,type,path,auth_code) "
                                        + "VALUES (1,'T03Sibling','Sibling','menu','user','t03:sibling:list')"));
        // Same child path under another parent is allowed.
        jdbc.update(
                "INSERT INTO sys_menu (name,title,type,path) VALUES ('T03Catalog','Test','catalog','/t03')");
        Long parent =
                jdbc.queryForObject("SELECT id FROM sys_menu WHERE name='T03Catalog'", Long.class);
        jdbc.update(
                "INSERT INTO sys_menu (parent_id,name,title,type,path,auth_code) VALUES "
                        + "(?, 'T03Child', 'Child', 'menu', 'user', 't03:child:list')",
                parent);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_menu WHERE name='T03Child'", Integer.class));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "INSERT INTO sys_role (role_code,role_name) VALUES ('super_admin','Duplicate')"));
    }
}
