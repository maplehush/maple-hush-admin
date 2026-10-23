-- 将系统菜单图标标识对齐官方 Playground 的真实路由配置。
-- 字典和日志页面为本项目扩展页面，使用同一图标集的语义图标。
UPDATE sys_menu
SET icon = CASE name
    WHEN 'System' THEN 'ion:settings-outline'
    WHEN 'SystemUser' THEN 'mdi:user'
    WHEN 'SystemRole' THEN 'mdi:account-group'
    WHEN 'SystemMenu' THEN 'mdi:menu'
    WHEN 'SystemDept' THEN 'charm:organisation'
    WHEN 'SystemDict' THEN 'mdi:book-cog-outline'
    WHEN 'SystemLoginLog' THEN 'mdi:login'
    WHEN 'SystemOperationLog' THEN 'mdi:text-box-outline'
END
WHERE name IN (
    'System',
    'SystemUser',
    'SystemRole',
    'SystemMenu',
    'SystemDept',
    'SystemDict',
    'SystemLoginLog',
    'SystemOperationLog'
)
  AND (name = 'System' OR type = 'menu');
