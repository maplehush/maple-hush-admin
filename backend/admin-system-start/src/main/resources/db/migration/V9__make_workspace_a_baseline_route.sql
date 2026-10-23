-- The workspace is an authenticated-user baseline, not a role permission.
UPDATE sys_user AS user_node
SET auth_version = auth_version + 1
WHERE EXISTS (
    SELECT 1
    FROM sys_user_role AS user_role
    INNER JOIN sys_role_menu AS role_menu
        ON role_menu.role_id = user_role.role_id
    INNER JOIN sys_menu AS menu_node
        ON menu_node.id = role_menu.menu_id
    WHERE user_role.user_id = user_node.id
      AND menu_node.name IN ('Dashboard', 'Workspace')
);

DELETE role_menu
FROM sys_role_menu AS role_menu
INNER JOIN sys_menu AS menu_node
    ON menu_node.id = role_menu.menu_id
WHERE menu_node.name IN ('Dashboard', 'Workspace');
