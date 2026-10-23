-- 审计日志属于系统管理功能，将日志目录放回系统管理层级。
-- 新路径变为 /system/log/{login-log,operation-log}，历史入口需同步更新。
UPDATE sys_menu
SET parent_id = 1,
    path = 'log',
    sort = 60
WHERE name = 'LogManagement'
  AND type = 'catalog';
SET @log_management_id = (
    SELECT id FROM sys_menu WHERE name = 'LogManagement' AND type = 'catalog' LIMIT 1
);

UPDATE sys_menu
SET redirect = CASE redirect
    WHEN '/log/login-log' THEN '/system/log/login-log'
    WHEN '/log/operation-log' THEN '/system/log/operation-log'
END
WHERE redirect IN ('/log/login-log', '/log/operation-log');

UPDATE sys_menu
SET meta_json = JSON_SET(meta_json, '$.activePath',
    CASE JSON_UNQUOTE(JSON_EXTRACT(meta_json, '$.activePath'))
        WHEN '/log/login-log' THEN '/system/log/login-log'
        WHEN '/log/operation-log' THEN '/system/log/operation-log'
    END)
WHERE JSON_UNQUOTE(JSON_EXTRACT(meta_json, '$.activePath'))
    IN ('/log/login-log', '/log/operation-log');

-- 路由层级变化后，让已获授日志目录或日志页面的用户重新加载动态路由。
UPDATE sys_user u
SET u.auth_version = u.auth_version + 1
WHERE EXISTS (
    SELECT 1
    FROM sys_user_role ur
    JOIN sys_role_menu rm ON rm.role_id = ur.role_id
    JOIN sys_menu m ON m.id = rm.menu_id
    WHERE ur.user_id = u.id
      AND (
          m.id = @log_management_id
          OR m.parent_id = @log_management_id
      )
);
