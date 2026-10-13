-- T03 第一份 SQL：MySQL 8.4 / InnoDB 建库建表草案，尚未执行。
-- 仅供全新独立数据库手工初始化和设计评审；若由 Compose + Flyway 部署，不要手工执行本文件。
-- T03 转成 Flyway V1__create_system_tables.sql 时，移除 CREATE DATABASE 和 USE，数据库由部署配置创建和选择。
-- T01 确认实际 Vben 版本后复核字段；本文件不是可直接投入工程的 Flyway 迁移。
-- 主键为 Java Long 对应的 BIGINT，API ID 输出为字符串。
-- DATETIME 精确到秒且不携带时区；本项目按 Asia/Shanghai 解释时间值。
-- CURRENT_TIMESTAMP 依赖 MySQL 会话时区；T03 必须固定东八区并验证往返。
-- status: 1 启用、0 停用。树根 parent_id 为 NULL，API 层负责转换 Vben 的 pid。

CREATE DATABASE maple_hush_admin
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE maple_hush_admin;

CREATE TABLE sys_dept (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sys_dept_parent_sort (parent_id, sort),
    CONSTRAINT fk_sys_dept_parent FOREIGN KEY (parent_id) REFERENCES sys_dept (id) ON DELETE RESTRICT,
    CONSTRAINT chk_sys_dept_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统部门';

CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dept_id BIGINT NULL,
    username VARCHAR(64) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(1024) NULL,
    introduction VARCHAR(500) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    must_change_password TINYINT NOT NULL DEFAULT 1,
    auth_version BIGINT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_dept_status (dept_id, status),
    CONSTRAINT fk_sys_user_dept FOREIGN KEY (dept_id) REFERENCES sys_dept (id) ON DELETE RESTRICT,
    CONSTRAINT chk_sys_user_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_sys_user_change_password CHECK (must_change_password IN (0, 1)),
    CONSTRAINT chk_sys_user_auth_version CHECK (auth_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户';

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    is_builtin TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code),
    CONSTRAINT chk_sys_role_builtin CHECK (is_builtin IN (0, 1)),
    CONSTRAINT chk_sys_role_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色';

CREATE TABLE sys_menu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    type VARCHAR(16) NOT NULL,
    path VARCHAR(255) NULL,
    component VARCHAR(255) NULL,
    redirect VARCHAR(255) NULL,
    auth_code VARCHAR(120) NULL,
    icon VARCHAR(200) NULL,
    meta_json JSON NULL,
    sort INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_menu_name (name),
    UNIQUE KEY uk_sys_menu_parent_path ((COALESCE(parent_id, 0)), path),
    UNIQUE KEY uk_sys_menu_auth_code (auth_code),
    KEY idx_sys_menu_parent_sort (parent_id, sort),
    CONSTRAINT fk_sys_menu_parent FOREIGN KEY (parent_id) REFERENCES sys_menu (id) ON DELETE RESTRICT,
    CONSTRAINT chk_sys_menu_type CHECK (type IN ('catalog', 'menu', 'embedded', 'link', 'button')),
    CONSTRAINT chk_sys_menu_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_sys_menu_button_path CHECK (
        (type = 'button' AND path IS NULL AND auth_code IS NOT NULL)
        OR (type <> 'button' AND path IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统菜单与按钮';

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role_user (role_id, user_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色授权';

CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, menu_id),
    KEY idx_sys_role_menu_menu_role (menu_id, role_id),
    CONSTRAINT fk_sys_role_menu_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sys_role_menu_menu FOREIGN KEY (menu_id) REFERENCES sys_menu (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单授权';

CREATE TABLE sys_dict_type (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dict_code VARCHAR(100) NOT NULL,
    dict_name VARCHAR(100) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_type_code (dict_code),
    CONSTRAINT chk_sys_dict_type_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型';

CREATE TABLE sys_dict_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dict_type_id BIGINT NOT NULL,
    label VARCHAR(200) NOT NULL,
    value VARCHAR(200) NOT NULL,
    color VARCHAR(7) NULL,
    sort INT NOT NULL DEFAULT 0,
    is_default TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_item_type_value (dict_type_id, value),
    KEY idx_sys_dict_item_type_sort (dict_type_id, sort),
    CONSTRAINT fk_sys_dict_item_type FOREIGN KEY (dict_type_id) REFERENCES sys_dict_type (id) ON DELETE RESTRICT,
    CONSTRAINT chk_sys_dict_item_color CHECK (color IS NULL OR REGEXP_LIKE(color, '^#[0-9A-Fa-f]{6}$')),
    CONSTRAINT chk_sys_dict_item_default CHECK (is_default IN (0, 1)),
    CONSTRAINT chk_sys_dict_item_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典项';

CREATE TABLE auth_refresh_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    family_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expire_time DATETIME NOT NULL,
    used_time DATETIME NULL,
    revoked_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_refresh_token_hash (token_hash),
    KEY idx_auth_refresh_user (user_id, expire_time),
    KEY idx_auth_refresh_family (family_id),
    KEY idx_auth_refresh_expire (expire_time),
    KEY idx_auth_refresh_revoked (revoked_time),
    CONSTRAINT fk_auth_refresh_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='刷新会话，仅存令牌哈希';

CREATE TABLE sys_login_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    username VARCHAR(64) NULL,
    event_type VARCHAR(32) NOT NULL,
    success TINYINT NOT NULL,
    reason_code VARCHAR(64) NULL,
    client_ip VARCHAR(45) NULL,
    user_agent VARCHAR(512) NULL,
    trace_id VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sys_login_log_time (create_time, id),
    KEY idx_sys_login_log_user_time (user_id, create_time),
    KEY idx_sys_login_log_username_time (username, create_time),
    CONSTRAINT chk_sys_login_log_success CHECK (success IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录与退出日志';

CREATE TABLE sys_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    actor_username VARCHAR(64) NULL,
    operation_code VARCHAR(120) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_id VARCHAR(64) NULL,
    success TINYINT NOT NULL,
    summary VARCHAR(1000) NULL,
    request_method VARCHAR(10) NULL,
    request_path VARCHAR(500) NULL,
    client_ip VARCHAR(45) NULL,
    trace_id VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sys_operation_log_time (create_time, id),
    KEY idx_sys_operation_log_actor_time (actor_user_id, create_time),
    KEY idx_sys_operation_log_target (target_type, target_id),
    CONSTRAINT chk_sys_operation_log_success CHECK (success IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关键管理操作日志';
