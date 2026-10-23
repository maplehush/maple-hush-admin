import type { RouteRecordStringComponent } from '@vben/types';

const menuTitleKeys: Record<string, string> = {
  '/dashboard': 'page.dashboard.title',
  '/dashboard/workspace': 'page.dashboard.workspace',
  '/dashboard/workspace/index': 'page.dashboard.workspace',
  '/system': 'system.title',
  '/system/dept': 'system.dept.title',
  '/system/dept/list': 'system.dept.title',
  '/system/dict': 'system.dict.title',
  '/system/dict/list': 'system.dict.title',
  '/system/log': 'system.log.title',
  '/system/log/login-log': 'system.log.loginTitle',
  '/system/log/login-log/list': 'system.log.loginTitle',
  '/system/log/operation-log': 'system.log.operationTitle',
  '/system/log/operation-log/list': 'system.log.operationTitle',
  '/system/login-log/list': 'system.log.loginTitle',
  '/system/menu': 'system.menu.title',
  '/system/menu/list': 'system.menu.title',
  '/system/operation-log/list': 'system.log.operationTitle',
  '/system/role': 'system.role.title',
  '/system/role/list': 'system.role.title',
  '/system/user': 'system.user.title',
  '/system/user/list': 'system.user.title',
};

/**
 * 为固定产品菜单写入语言资源键；保持路由 name 不变，并保留未知自定义菜单标题。
 */
function localizeMenuTitleKeys(
  routes: RouteRecordStringComponent[],
): RouteRecordStringComponent[] {
  return routes.map((route) => {
    const titleKey = menuTitleKeys[route.path.replace(/\/$/, '')];
    const children = route.children
      ? localizeMenuTitleKeys(route.children)
      : undefined;

    return {
      ...route,
      meta: titleKey ? { ...route.meta, title: titleKey } : route.meta,
      ...(children ? { children } : {}),
    };
  });
}

export { localizeMenuTitleKeys };
