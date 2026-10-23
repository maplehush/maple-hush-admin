import type { RouteRecordRaw } from 'vue-router';
const coreRoutes: RouteRecordRaw[] = [
  {
    name: 'Root',
    path: '/',
    component: () => import('#/layouts/basic.vue'),
    redirect: '/dashboard/workspace',
    meta: { hideInBreadcrumb: true, title: 'page.root' },
    children: [
      {
        name: 'Profile',
        path: '/profile',
        component: () => import('#/views/_core/profile/index.vue'),
        meta: { title: 'page.profile.title', hideInMenu: true },
      },
      {
        name: 'Forbidden',
        path: '/forbidden',
        component: () => import('#/views/_core/fallback/forbidden.vue'),
        meta: { title: 'page.fallback.forbidden', hideInMenu: true },
      },
    ],
  },
  {
    name: 'Authentication',
    path: '/auth',
    redirect: '/auth/login',
    component: () => import('#/layouts/auth.vue'),
    meta: { hideInTab: true, title: 'page.auth.title' },
    children: [
      {
        name: 'Login',
        path: 'login',
        component: () => import('#/views/_core/authentication/login.vue'),
        meta: { title: 'page.auth.login' },
      },
      {
        name: 'InitialPassword',
        path: 'initial-password',
        component: () =>
          import('#/views/_core/authentication/initial-password.vue'),
        meta: { title: 'page.auth.initialPassword' },
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
