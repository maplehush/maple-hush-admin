-- 登录默认页作为正式菜单路由，由动态菜单授权统一控制可见性。
INSERT INTO sys_menu
    (parent_id, name, title, type, path, component, auth_code, icon, sort, status)
VALUES
    (NULL, 'Dashboard', '概览', 'catalog', '/dashboard', NULL, NULL, 'lucide:layout-dashboard', -1, 1);

SET @dashboard_menu_id = (
    SELECT id
    FROM sys_menu
    WHERE name = 'Dashboard' AND type = 'catalog'
    LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, name, title, type, path, component, auth_code, icon, sort, status)
VALUES
    (@dashboard_menu_id, 'Workspace', '工作台', 'menu', 'workspace', '/dashboard/workspace/index', NULL, 'carbon:workspace', 10, 1);

SET @workspace_menu_id = (
    SELECT id
    FROM sys_menu
    WHERE name = 'Workspace' AND type = 'menu'
    LIMIT 1
);

-- 工作台是所有角色的默认入口，既补齐既有角色，也覆盖当前新增角色。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role_node.id, menu_node.id
FROM sys_role AS role_node
CROSS JOIN sys_menu AS menu_node
WHERE menu_node.id IN (@dashboard_menu_id, @workspace_menu_id);

-- 新增动态路由后使当前会话重新加载授权与菜单。
UPDATE sys_user AS user_node
SET auth_version = auth_version + 1
WHERE EXISTS (
    SELECT 1
    FROM sys_user_role AS user_role
    INNER JOIN sys_role_menu AS role_menu
        ON role_menu.role_id = user_role.role_id
    WHERE user_role.user_id = user_node.id
      AND role_menu.menu_id IN (@dashboard_menu_id, @workspace_menu_id)
);
