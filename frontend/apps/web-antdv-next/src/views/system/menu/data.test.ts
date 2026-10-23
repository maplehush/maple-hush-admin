import type { SystemMenu } from '#/api/system/menu';

import { describe, expect, it, vi } from 'vitest';

import {
  isMenuParentRequired,
  menuPayload,
  normalizeOptionalPath,
  optionalActivePathRule,
  parentOptions,
  validateMenuNameAvailability,
  validateMenuPathAvailability,
  validateOptionalActivePath,
} from './data';

describe('menu DTO', () => {
  it('requires a parent for route and action menu types but not for directories', () => {
    expect(isMenuParentRequired('catalog')).toBe(false);
    expect(isMenuParentRequired('link')).toBe(false);
    for (const type of ['menu', 'embedded', 'button'] as const) {
      expect(isMenuParentRequired(type)).toBe(true);
    }
  });

  it('omits the route path for external links and retains their target URL', () => {
    const payload = menuPayload({
      name: 'Vben',
      type: 'link',
      status: 1,
      path: '/dashboard/vben',
      meta: { title: 'Vben 官网', link: 'https://www.vben.pro' },
    });

    expect(payload.path).toBeUndefined();
    expect(payload.meta.link).toBe('https://www.vben.pro');
  });

  it('preserves existing hidden advanced metadata without restoring cleared visible fields', () => {
    const input = {
      name: 'Page',
      type: 'menu' as const,
      status: 1 as const,
      path: '/page',
      component: '/system/user/list',
      meta: { title: '页面', activePath: '' },
    };
    const original = {
      title: '旧标题',
      affixTabOrder: 2,
      maxNumOfOpenTab: 3,
      breadcrumbUseActivePath: true,
      domCached: false,
      fullPathKey: true,
      noBasicLayout: false,
      openInNewWindow: true,
      activePath: '/system/user',
    };
    const payload = menuPayload(input, original);
    expect(payload.meta).toEqual({
      ...original,
      title: '页面',
      activePath: undefined,
    });
    expect(
      menuPayload(
        { ...input, type: 'button', authCode: 'system:user:create' },
        original,
      ).meta,
    ).toEqual({ title: '页面', icon: undefined, order: undefined });
    expect(menuPayload(input).meta).toEqual({ title: '页面' });
  });
  it('sends the same normalized activation path that was validated', () => {
    expect(
      menuPayload({
        name: 'Page',
        type: 'menu',
        status: 1,
        path: '/page',
        component: '/system/user/list',
        meta: { title: '页面', activePath: ' /system/user ' },
      }).meta.activePath,
    ).toBe('/system/user');
  });
  it('removes cleared activation path and optional text metadata from update payload', () => {
    const value = menuPayload({
      name: 'Page',
      type: 'menu',
      status: 1,
      path: '/page',
      component: '/system/user/list',
      meta: { title: '页面', activePath: '', activeIcon: '  ', badge: '' },
    });
    expect(value.meta).toEqual({ title: '页面' });
  });
  it('drops cleared numeric metadata rather than sending null to strict DTO', () => {
    const value = menuPayload({
      name: 'Page',
      type: 'menu',
      status: 1,
      path: '/page',
      component: '/system/user/list',
      meta: { title: ' 页面 ', affixTabOrder: null as unknown as number },
    });
    expect(value.meta).toEqual({ title: '页面' });
  });
  it('does not retain a stale page component when switching to a directory', () => {
    const value = menuPayload({
      name: 'Dir',
      type: 'catalog',
      status: 1,
      path: '/dir',
      component: '/system/user/list',
      meta: { title: '目录' },
    });
    expect(value.component).toBeUndefined();
  });
  it('strips stale route fields and metadata when switching to button', () => {
    expect(
      menuPayload({
        name: 'CreateUser',
        type: 'button',
        pid: '10',
        status: 1,
        path: '/users',
        component: '/system/user/list',
        authCode: 'system:user:create',
        meta: { title: '新增', icon: 'lucide:plus', order: 2, keepAlive: true },
      }),
    ).toEqual({
      name: 'CreateUser',
      type: 'button',
      pid: '10',
      status: 1,
      authCode: 'system:user:create',
      meta: { title: '新增', icon: 'lucide:plus', order: 2 },
    });
  });
  it('normalizes iframe component and removes stale external link', () => {
    const value = menuPayload({
      name: 'Docs',
      type: 'embedded',
      status: 1,
      path: '/docs',
      component: '/old',
      meta: {
        title: '文档',
        link: 'https://wrong.test',
        iframeSrc: 'https://docs.test',
      },
    });
    expect(value.component).toBe('IFrameView');
    expect(value.meta).not.toHaveProperty('link');
  });
  it('excludes an edited node and its descendants', () => {
    expect(
      parentOptions(
        [
          {
            id: '1',
            name: 'Root',
            type: 'catalog',
            status: 1,
            meta: { title: '根' },
            children: [
              {
                id: '2',
                name: 'Child',
                type: 'catalog',
                status: 1,
                meta: { title: '子' },
              },
            ],
          },
        ],
        '1',
        'menu',
      ),
    ).toEqual([]);
  });
  it('uses string values for parent options so numeric API ids resolve to labels', () => {
    expect(
      parentOptions(
        [
          {
            id: 1,
            name: 'Dashboard',
            type: 'catalog',
            status: 1,
            meta: { title: '概况' },
          } as unknown as SystemMenu,
        ],
        undefined,
        'menu',
      ),
    ).toEqual([{ value: '1', title: '概况 (Dashboard)' }]);
  });
});

describe('optional menu activation path validation', () => {
  it('supports Vben synchronous optional detection and asynchronous field validation', async () => {
    const exists = vi.fn(async (path: string) => path === '/system/user');
    const rule = optionalActivePathRule(exists);
    expect(rule.isOptional()).toBe(true);
    expect((await rule.safeParseAsync('')).success).toBe(true);
    expect((await rule.safeParseAsync('/missing')).success).toBe(false);
    expect((await rule.safeParseAsync(' /system/user ')).success).toBe(true);
    expect(exists.mock.calls).toEqual([['/missing'], ['/system/user']]);
  });
  it('normalizes cleared values to undefined and never requests existence for them', async () => {
    const exists = vi.fn(async () => true);
    for (const value of ['', '  ', undefined]) {
      expect(normalizeOptionalPath(value)).toBeUndefined();
      expect(await validateOptionalActivePath(value, exists)).toBe(true);
    }
    expect(exists).not.toHaveBeenCalled();
  });
  it('checks a supplied static path and rejects an unavailable target', async () => {
    const exists = vi.fn(async (path: string) => path === '/system/user');
    expect(await validateOptionalActivePath('/system/user', exists)).toBe(true);
    expect(await validateOptionalActivePath('/missing', exists)).toBe(false);
    expect(exists.mock.calls).toEqual([['/system/user'], ['/missing']]);
  });
  it('rejects malformed static paths before calling the server', async () => {
    const exists = vi.fn(async () => true);
    for (const value of [
      'system/user',
      '/system/user?tab=1',
      '/system/',
      '/system/:id',
      `/${'a'.repeat(255)}`,
    ]) {
      expect(await validateOptionalActivePath(value, exists)).toBe(false);
    }
    expect(exists).not.toHaveBeenCalled();
  });
});

describe('menu name availability validation', () => {
  it('does not query blank or malformed menu names', async () => {
    const exists = vi.fn(async () => false);
    for (const value of ['', '  ', '123-invalid']) {
      expect(
        await validateMenuNameAvailability(value, exists, '查重失败'),
      ).toBe(true);
    }
    expect(exists).not.toHaveBeenCalled();
  });

  it('checks valid names and converts request failures to a readable message', async () => {
    const exists = vi.fn(async (name: string) => name === 'ExistingMenu');
    expect(
      await validateMenuNameAvailability(' NewMenu ', exists, '查重失败'),
    ).toBe(true);
    expect(
      await validateMenuNameAvailability('ExistingMenu', exists, '查重失败'),
    ).toBe(false);
    expect(exists.mock.calls).toEqual([['NewMenu'], ['ExistingMenu']]);

    const failedRequest = vi.fn(async () => {
      throw new Error('Request failed with status code 400');
    });
    await expect(
      validateMenuNameAvailability(
        'ValidMenu',
        failedRequest,
        '菜单名称校验失败，请稍后重试',
      ),
    ).rejects.toThrow('菜单名称校验失败，请稍后重试');
  });
});

describe('menu route path availability validation', () => {
  it('does not query blank or malformed route paths', async () => {
    const exists = vi.fn(async () => true);
    for (const value of [
      '',
      '  ',
      'system/user',
      '/system/user?tab=1',
      '/system/:id',
      '/system/',
      `/${'a'.repeat(255)}`,
    ]) {
      expect(
        await validateMenuPathAvailability(value, exists, '路由地址校验失败'),
      ).toBe(true);
    }
    expect(exists).not.toHaveBeenCalled();
  });

  it('checks normalized valid paths and converts request failures to a readable message', async () => {
    const exists = vi.fn(async (path: string) => path === '/system/user');
    expect(
      await validateMenuPathAvailability(
        ' /system/role ',
        exists,
        '路由地址校验失败',
      ),
    ).toBe(true);
    expect(
      await validateMenuPathAvailability(
        '/system/user',
        exists,
        '路由地址校验失败',
      ),
    ).toBe(false);
    expect(exists.mock.calls).toEqual([['/system/role'], ['/system/user']]);

    const failedRequest = vi.fn(async () => {
      throw new Error('Request failed with status code 400');
    });
    await expect(
      validateMenuPathAvailability(
        '/system/role',
        failedRequest,
        '路由地址校验失败，请稍后重试',
      ),
    ).rejects.toThrow('路由地址校验失败，请稍后重试');
  });
});

import { collectUndeletableMenuIds } from './data';

describe('menu delete policy', () => {
  it('protects the system menu subtree and baseline workspace entries only', () => {
    const ids = collectUndeletableMenuIds([
      {
        id: 'dashboard',
        name: 'Dashboard',
        children: [
          { id: 'workspace', name: 'Workspace' },
          { id: 'custom-dashboard-child', name: 'CustomDashboardChild' },
        ],
      },
      {
        id: 'system',
        name: 'System',
        children: [
          { id: 'user-menu', name: 'SystemUser' },
          {
            id: 'custom-system-menu',
            name: 'CustomSystemMenu',
            children: [
              { id: 'custom-system-button', name: 'CustomSystemButton' },
            ],
          },
        ],
      },
    ]);

    expect(ids).toEqual(
      new Set([
        'custom-system-button',
        'custom-system-menu',
        'dashboard',
        'system',
        'user-menu',
        'workspace',
      ]),
    );
    expect(ids.has('custom-dashboard-child')).toBe(false);
  });
});
