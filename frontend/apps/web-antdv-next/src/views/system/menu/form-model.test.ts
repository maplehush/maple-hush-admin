import { describe, expect, it, vi } from 'vitest';

import {
  menuPayload,
  normalizeOptionalPath,
  optionalActivePathRule,
  parentOptions,
  validateOptionalActivePath,
} from './form-model';

describe('menu DTO', () => {
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
