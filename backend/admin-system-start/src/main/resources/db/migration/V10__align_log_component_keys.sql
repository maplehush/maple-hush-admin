-- Keep log page component keys aligned with their views/log source directories.
UPDATE sys_menu
SET component = CASE auth_code
    WHEN 'system:login-log:list' THEN '/log/login/list'
    WHEN 'system:operation-log:list' THEN '/log/operation/list'
END
WHERE type = 'menu'
  AND (
      (auth_code = 'system:login-log:list' AND component = '/system/login-log/list')
      OR (auth_code = 'system:operation-log:list' AND component = '/system/operation-log/list')
  );
