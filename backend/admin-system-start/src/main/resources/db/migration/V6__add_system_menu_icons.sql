-- 为系统管理页面补齐与 Vben 官方菜单示例一致的 Lucide 图标。
-- 保留既有父目录图标；本迁移只更新菜单展示元数据，不改变路径、权限或授权关系。
UPDATE sys_menu
SET icon = CASE name
    WHEN 'SystemUser' THEN 'lucide:user-round'
    WHEN 'SystemRole' THEN 'lucide:users-round'
    WHEN 'SystemMenu' THEN 'lucide:menu'
    WHEN 'SystemDept' THEN 'lucide:network'
    WHEN 'SystemDict' THEN 'lucide:book-cog'
    WHEN 'SystemLoginLog' THEN 'lucide:log-in'
    WHEN 'SystemOperationLog' THEN 'lucide:scroll-text'
END
WHERE name IN (
    'SystemUser',
    'SystemRole',
    'SystemMenu',
    'SystemDept',
    'SystemDict',
    'SystemLoginLog',
    'SystemOperationLog'
)
  AND type = 'menu';
