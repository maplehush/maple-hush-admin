-- 日志管理独立为一级目录；不改历史迁移、页面 ID、权限码或组件键。
-- 使用自增 ID，兼容已有自定义菜单占用后续 ID 的开发库。
INSERT INTO sys_menu (name, title, type, path, icon, sort, status)
VALUES ('LogManagement', '日志管理', 'catalog', '/log', 'lucide:logs', 20, 1);
SET @log_management_id = LAST_INSERT_ID();

UPDATE sys_menu
SET parent_id = @log_management_id
WHERE name IN ('SystemLoginLog', 'SystemOperationLog')
  AND auth_code IN ('system:login-log:list', 'system:operation-log:list');

-- 只补目录关联，不扩大任何普通角色的日志页面权限。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, @log_management_id
FROM sys_role r
WHERE r.is_builtin = 1
   OR EXISTS (
       SELECT 1 FROM sys_role_menu rm
       JOIN sys_menu m ON m.id = rm.menu_id
       WHERE rm.role_id = r.id
         AND m.parent_id = @log_management_id
   );

-- 已有菜单如果引用两条旧页面地址，同步到迁移后的完整路径。
UPDATE sys_menu
SET redirect = CASE redirect
    WHEN '/system/login-log' THEN '/log/login-log'
    WHEN '/system/operation-log' THEN '/log/operation-log'
END
WHERE redirect IN ('/system/login-log', '/system/operation-log');

UPDATE sys_menu
SET meta_json = JSON_SET(meta_json, '$.activePath',
    CASE JSON_UNQUOTE(JSON_EXTRACT(meta_json, '$.activePath'))
        WHEN '/system/login-log' THEN '/log/login-log'
        WHEN '/system/operation-log' THEN '/log/operation-log'
    END)
WHERE JSON_UNQUOTE(JSON_EXTRACT(meta_json, '$.activePath'))
    IN ('/system/login-log', '/system/operation-log');

-- 旧 access/refresh 下次使用时按授权版本失效，重新登录后加载新菜单树。
UPDATE sys_user u
SET u.auth_version = u.auth_version + 1
WHERE EXISTS (
    SELECT 1 FROM sys_user_role ur
    JOIN sys_role_menu rm ON rm.role_id = ur.role_id
    WHERE ur.user_id = u.id AND rm.menu_id = @log_management_id
);
