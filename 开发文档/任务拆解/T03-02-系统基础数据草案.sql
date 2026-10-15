-- T03 第二份 SQL：系统基础数据草案，须在第一份建库建表 SQL 之后执行；尚未执行。
-- 仅供全新独立数据库手工初始化和设计评审；若由 Compose + Flyway 部署，不要手工执行本文件。
-- T03 转成 Flyway V2__seed_system_data.sql 时移除 USE 及手工执行的事务包装，并按 T01 的实际前端版本复核路由和权限码。
-- 不写入管理员用户或密码。T04 通过显式开启的首次引导创建首个超级管理员。
-- 菜单 ID 仅用于本次种子数据的父子关联，Java 代码必须按角色编码和菜单名称/权限码查询，不依赖固定 ID。
-- 子菜单 path 按 Vben 嵌套路由保存相对路径；component 使用 Vben 的无 .vue 后缀页面键。
-- T14 须在 web-antdv-next/src/views 下建立对应 .vue 文件，T15 再完成真实页面。

USE maple_hush_admin;
SET time_zone = '+08:00';

START TRANSACTION;

INSERT INTO sys_role (role_code, role_name, is_builtin, status, remark)
VALUES ('super_admin', '超级管理员', 1, 1, '系统内置角色，不允许删除、停用或通过普通角色表单改授权');

-- 当前设计含系统管理和日志管理两个根目录；71 仅为空库草案示例。
-- 产品升级运行 V4 并自动分配目录 ID，不修改已经执行的 V2。
INSERT INTO sys_menu
    (id, parent_id, name, title, type, path, component, redirect, auth_code, icon, sort, status)
VALUES
    (1, NULL, 'System', '系统管理', 'catalog', '/system', NULL, NULL, NULL, 'lucide:settings-2', 10, 1),
    (71, NULL, 'LogManagement', '日志管理', 'catalog', '/log', NULL, NULL, NULL, 'lucide:logs', 20, 1);

-- 首版七个页面；页面节点的 auth_code 是进入页面所需的查询权限。
INSERT INTO sys_menu
    (id, parent_id, name, title, type, path, component, redirect, auth_code, icon, sort, status)
VALUES
    (10, 1, 'SystemUser', '用户管理', 'menu', 'user', '/system/user/list', NULL, 'system:user:list', NULL, 10, 1),
    (20, 1, 'SystemRole', '角色管理', 'menu', 'role', '/system/role/list', NULL, 'system:role:list', NULL, 20, 1),
    (30, 1, 'SystemMenu', '菜单管理', 'menu', 'menu', '/system/menu/list', NULL, 'system:menu:list', NULL, 30, 1),
    (40, 1, 'SystemDept', '部门管理', 'menu', 'dept', '/system/dept/list', NULL, 'system:dept:list', NULL, 40, 1),
    (50, 1, 'SystemDict', '字典管理', 'menu', 'dict', '/system/dict/list', NULL, 'system:dict:list', NULL, 50, 1),
    (60, 71, 'SystemLoginLog', '登录日志', 'menu', 'login-log', '/system/login-log/list', NULL, 'system:login-log:list', NULL, 60, 1),
    (70, 71, 'SystemOperationLog', '操作日志', 'menu', 'operation-log', '/system/operation-log/list', NULL, 'system:operation-log:list', NULL, 70, 1);

-- 按钮节点只提供操作权限码，不进入当前用户的动态路由树。
INSERT INTO sys_menu
    (id, parent_id, name, title, type, auth_code, sort, status)
VALUES
    (11, 10, 'SystemUserCreate', '新增用户', 'button', 'system:user:create', 10, 1),
    (12, 10, 'SystemUserUpdate', '修改用户', 'button', 'system:user:update', 20, 1),
    (13, 10, 'SystemUserDelete', '删除用户', 'button', 'system:user:delete', 30, 1),
    (14, 10, 'SystemUserResetPassword', '重置密码', 'button', 'system:user:reset-password', 40, 1),
    (15, 10, 'SystemUserAssignRole', '分配角色', 'button', 'system:user:assign-role', 50, 1),
    (21, 20, 'SystemRoleCreate', '新增角色', 'button', 'system:role:create', 10, 1),
    (22, 20, 'SystemRoleUpdate', '修改角色', 'button', 'system:role:update', 20, 1),
    (23, 20, 'SystemRoleDelete', '删除角色', 'button', 'system:role:delete', 30, 1),
    (24, 20, 'SystemRoleGrant', '菜单授权', 'button', 'system:role:grant', 40, 1),
    (31, 30, 'SystemMenuCreate', '新增菜单', 'button', 'system:menu:create', 10, 1),
    (32, 30, 'SystemMenuUpdate', '修改菜单', 'button', 'system:menu:update', 20, 1),
    (33, 30, 'SystemMenuDelete', '删除菜单', 'button', 'system:menu:delete', 30, 1),
    (41, 40, 'SystemDeptCreate', '新增部门', 'button', 'system:dept:create', 10, 1),
    (42, 40, 'SystemDeptUpdate', '修改部门', 'button', 'system:dept:update', 20, 1),
    (43, 40, 'SystemDeptDelete', '删除部门', 'button', 'system:dept:delete', 30, 1),
    (51, 50, 'SystemDictCreate', '新增字典', 'button', 'system:dict:create', 10, 1),
    (52, 50, 'SystemDictUpdate', '修改字典', 'button', 'system:dict:update', 20, 1),
    (53, 50, 'SystemDictDelete', '删除字典', 'button', 'system:dict:delete', 30, 1);

-- 授予内置角色当前所有系统节点；后续新增菜单时，须在新增迁移或业务事务中同步授予该角色。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role AS r
CROSS JOIN sys_menu AS m
WHERE r.role_code = 'super_admin';

COMMIT;
