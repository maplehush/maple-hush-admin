import type { RouteRecordStringComponent } from '@vben/types';

import { describe, expect, it } from 'vitest';

import { localizeMenuTitleKeys } from './menu-locale';

describe('dynamic menu locale keys', () => {
  it('maps known system routes while preserving route identity and unknown titles', () => {
    const routes: RouteRecordStringComponent[] = [
      {
        component: 'BasicLayout',
        meta: { title: '系统管理' },
        name: 'System',
        path: '/system',
        children: [
          {
            component: 'system/user/list',
            meta: { title: '用户管理' },
            name: 'SystemUser',
            path: '/system/user/list',
          },
          {
            component: 'custom/page',
            meta: { title: '自定义页面' },
            name: 'CustomPage',
            path: '/custom/page',
          },
        ],
      },
    ];

    const localized = localizeMenuTitleKeys(routes);

    expect(localized[0]?.name).toBe('System');
    expect(localized[0]?.meta?.title).toBe('system.title');
    expect(localized[0]?.children?.[0]?.meta?.title).toBe('system.user.title');
    expect(localized[0]?.children?.[1]?.meta?.title).toBe('自定义页面');
  });
});
