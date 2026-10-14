package com.maplehush.admin.system.auth.bootstrap;

import com.maplehush.admin.system.auth.support.PasswordPolicy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 启动时检查唯一超管；维护模式只恢复原账号，不开放 HTTP 入口
 */
@Component
public class BootstrapGuard implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String username;
    private final boolean recovery;
    private final String recoveryOutput;

    public BootstrapGuard(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            PasswordEncoder encoder,
            @Value("${maple-hush.auth.bootstrap-enabled:false}") boolean enabled,
            @Value("${maple-hush.auth.bootstrap-username:admin}") String username,
            @Value("${maple-hush.maintenance.recover-super-admin:false}") boolean recovery,
            @Value("${maple-hush.maintenance.recovery-output:}") String recoveryOutput) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.encoder = encoder;
        this.enabled = enabled;
        this.username = username;
        this.recovery = recovery;
        this.recoveryOutput = recoveryOutput;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        if (recovery) {
            recover();
        } else {
            transactions.executeWithoutResult(status -> validateOrCreate());
        }
    }

    private void validateOrCreate() {
        Long roleId = lockedRoleId();
        Integer totalUsers = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
        List<Holder> holders = holders(roleId);
        if (totalUsers == 0 && holders.isEmpty() && enabled) {
            if (username.isBlank() || username.length() > 64) {
                throw new IllegalStateException("首次超管账号名无效");
            }
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update(
                    connection -> {
                        PreparedStatement statement =
                                connection.prepareStatement(
                                        "INSERT INTO sys_user (username,real_name,password,status,must_change_password,auth_version) "
                                                + "VALUES (?,?,?,1,1,0)",
                                        Statement.RETURN_GENERATED_KEYS);
                        statement.setString(1, username);
                        statement.setString(2, "超级管理员");
                        statement.setString(3, encoder.encode(PasswordPolicy.TEMPORARY_PASSWORD));
                        return statement;
                    },
                    key);
            jdbc.update(
                    "INSERT INTO sys_user_role (user_id,role_id) VALUES (?,?)",
                    key.getKey().longValue(),
                    roleId);
            return;
        }
        if (holders.size() != 1 || holders.getFirst().status() != 1) {
            throw new IllegalStateException("唯一超级管理员账号关联或状态异常，拒绝启动");
        }
    }

    private void recover() throws Exception {
        if (recoveryOutput.isBlank()) {
            throw new IllegalStateException("维护模式必须指定恢复密码输出文件");
        }
        Path output = Path.of(recoveryOutput).toAbsolutePath().normalize();
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        String temporaryPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Files.writeString(
                output,
                temporaryPassword + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        try {
            try {
                Files.setPosixFilePermissions(
                        output,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows ACL 由存放目录管理；运维须选用私有目录。
            }
            transactions.executeWithoutResult(
                    status -> {
                        Long roleId = lockedRoleId();
                        List<Holder> holders = holders(roleId);
                        if (holders.size() != 1) {
                            throw new IllegalStateException("超管关联异常，无法自动恢复");
                        }
                        long id = holders.getFirst().id();
                        jdbc.update(
                                "UPDATE sys_user SET password=?,status=1,must_change_password=1,auth_version=auth_version+1 "
                                        + "WHERE id=?",
                                encoder.encode(temporaryPassword),
                                id);
                        jdbc.update(
                                "UPDATE auth_refresh_session SET revoked_time=? WHERE user_id=? AND revoked_time IS NULL",
                                LocalDateTime.now(),
                                id);
                        jdbc.update(
                                "INSERT INTO sys_operation_log (actor_user_id,operation_code,target_type,target_id,success,summary) "
                                        + "VALUES (NULL,'auth:super-admin-recovery','sys_user',?,1,'离线恢复原超级管理员账号')",
                                Long.toString(id));
                    });
        } catch (Exception exception) {
            Files.deleteIfExists(output);
            throw exception;
        }
    }

    private Long lockedRoleId() {
        List<Long> ids =
                jdbc.queryForList(
                        "SELECT id FROM sys_role WHERE role_code='super_admin' "
                                + "AND is_builtin=1 AND status=1 FOR UPDATE",
                        Long.class);
        if (ids.size() != 1) {
            throw new IllegalStateException("内置超级管理员角色缺失或状态异常");
        }
        return ids.getFirst();
    }

    private List<Holder> holders(long roleId) {
        return jdbc.query(
                "SELECT u.id,u.status FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id "
                        + "WHERE ur.role_id=?",
                (rs, row) -> new Holder(rs.getLong(1), rs.getInt(2)),
                roleId);
    }

    private record Holder(long id, int status) {}
}
