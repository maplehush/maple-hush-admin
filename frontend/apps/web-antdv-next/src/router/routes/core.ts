import type { RouteRecordRaw } from 'vue-router';
const coreRoutes: RouteRecordRaw[] = [
  {
    name: 'Root',
    path: '/',
    component: () => import('#/layouts/basic.vue'),
    redirect: '/dashboard/workspace',
    meta: { hideInBreadcrumb: true, title: 'Root' },
    children: [
      {
        name: 'Workspace',
        path: '/dashboard/workspace',
        component: () => import('#/views/_core/home/index.vue'),
        meta: { title: '工作台' },
      },
      {
        name: 'Profile',
        path: '/profile',
        component: () => import('#/views/_core/profile/index.vue'),
        meta: { title: '个人中心', hideInMenu: true },
      },
      {
        name: 'Forbidden',
        path: '/forbidden',
        component: () => import('#/views/_core/fallback/forbidden.vue'),
        meta: { title: '无权访问', hideInMenu: true },
      },
    ],
  },
  {
    name: 'Authentication',
    path: '/auth',
    redirect: '/auth/login',
    component: () => import('#/layouts/auth.vue'),
    meta: { hideInTab: true, title: '登录认证' },
    children: [
      {
        name: 'Login',
        path: 'login',
        component: () => import('#/views/_core/authentication/login.vue'),
        meta: { title: '登录' },
      },
      {
        name: 'InitialPassword',
        path: 'initial-password',
        component: () =>
          import('#/views/_core/authentication/initial-password.vue'),
        meta: { title: '首次登录修改密码' },
      },
    ],
  },
];
const fallbackNotFoundRoute: RouteRecordRaw = {
  name: 'FallbackNotFound',
  path: '/:path(.*)*',
  component: () => import('#/views/_core/fallback/not-found.vue'),
  meta: {
    hideInBreadcrumb: true,
    hideInMenu: true,
    hideInTab: true,
    title: '404',
  },
};
export { coreRoutes, fallbackNotFoundRoute };
