import type { Router } from 'vue-router';

import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';
import { startProgress, stopProgress } from '@vben/utils';

import { message } from 'antdv-next';

import { session } from '#/auth/session';
import { accessRoutes, coreRouteNames } from '#/router/routes';
import { useAuthStore } from '#/store';

import { generateAccess } from './access';
export function createRouterGuard(router: Router) {
  router.beforeEach(async (to) => {
    if (preferences.transition.progress) {
      startProgress();
    }
    const access = useAccessStore();
    const auth = useAuthStore();
    if (to.name === 'InitialPassword') {
      return auth.passwordChangeToken ? true : '/auth/login';
    }
    if (coreRouteNames.includes(String(to.name))) {
      auth.passwordChangeToken = null;
      return session.state.token ? '/dashboard/workspace' : true;
    }
    if (!session.state.token) {
      return {
        path: '/auth/login',
        query: { redirect: to.fullPath },
        replace: true,
      };
    }
    if (access.isAccessChecked) {
      return to.name === 'FallbackNotFound' && to.path.startsWith('/system')
        ? '/forbidden'
        : true;
    }
    const epoch = session.state.epoch;
    try {
      const info = await auth.loadIdentity();
      const { accessibleMenus, accessibleRoutes } = await generateAccess({
        roles: info.roles ?? [],
        router,
        routes: accessRoutes,
      });
      if (epoch !== session.state.epoch) {
        return '/auth/login';
      }
      access.setAccessMenus(accessibleMenus);
      access.setAccessRoutes(accessibleRoutes);
      access.setIsAccessChecked(true);
      return { path: to.fullPath, replace: true };
    } catch {
      if (!session.state.token) {
        return '/auth/login';
      }
      message.error('用户或权限加载失败，请稍后刷新重试');
      return false;
    }
  });
  router.afterEach(() => stopProgress());
  router.onError(() => stopProgress());
}
