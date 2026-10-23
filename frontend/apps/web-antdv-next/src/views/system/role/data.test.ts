import type { SystemMenu } from '#/api/system/menu';

import { describe, expect, it } from 'vitest';

import {
  affectsCurrentRoles,
  mergeRoleValues,
  roleGrantMenuTree,
  rolePayload,
  rolePermissionTreeProps,
  rolePermissionValues,
} from './data';
import { useFormSchema } from './data';

describe('role authorization boundaries', () => {
  const role = {
    id: '3',
    roleCode: 'operator',
    name: '操作员',
    status: 1 as const,
    builtIn: false,
    permissions: ['10'],
    remark: '',
  };
  it('omits immutable code and permissions in attribute updates', () => {
    expect(rolePayload(role, false)).toEqual({
      name: '操作员',
      remark: '',
      status: 1,
    });
  });
  it('allows explicitly clearing all permissions', () => {
    expect(
      rolePayload({ ...role, permissions: [] }, false, true).permissions,
    ).toEqual([]);
  });
  it('sends only permissions from the menu-permission editor', () => {
    expect(mergeRoleValues(role, { permissions: ['11'] }, true)).toEqual({
      ...role,
      permissions: ['11'],
    });
  });
  it('shows only role fields while editing and only permissions in grant mode', () => {
    expect(
      useFormSchema({ mode: 'edit' }).map(({ fieldName }) => fieldName),
    ).toEqual(['roleCode', 'name', 'status', 'remark']);
    expect(
      useFormSchema({ mode: 'grant' }).map(({ fieldName }) => fieldName),
    ).toEqual(['permissions']);
  });
  it('includes permissions when creating a role with grant access', () => {
    expect(
      useFormSchema({ mode: 'create', canGrant: true }).map(
        ({ fieldName }) => fieldName,
      ),
    ).toContain('permissions');
  });

  it('keeps granted directory ancestors in initial selections for checked-state display', () => {
    const tree: SystemMenu[] = [
      {
        id: '1',
        name: 'Logs',
        type: 'catalog',
        status: 1,
        meta: { title: '日志管理' },
        children: [
          {
            id: '2',
            name: 'LoginLog',
            type: 'menu',
            status: 1,
            path: 'login-log',
            meta: { title: '登录日志' },
          },
          {
            id: '3',
            name: 'OperationLog',
            type: 'menu',
            status: 1,
            path: 'operation-log',
            meta: { title: '操作日志' },
          },
        ],
      },
    ];

    expect(rolePermissionTreeProps).toMatchObject({
      multiple: true,
      checkStrictly: false,
    });
    expect(rolePermissionValues(tree, ['1', '2', '3']).sort()).toEqual([
      '1',
      '2',
      '3',
    ]);
    expect(rolePermissionValues(tree, ['1', '2'])).toEqual(['2']);
  });
  it('only authorization/status changes affect a current holder', () => {
    expect(
      affectsCurrentRoles(role, { ...role, name: '新名' }, ['operator']),
    ).toBe(false);
    expect(
      affectsCurrentRoles(role, { ...role, status: 0 }, ['operator']),
    ).toBe(true);
    expect(
      affectsCurrentRoles(role, { ...role, permissions: [] }, ['another']),
    ).toBe(false);
    expect(
      affectsCurrentRoles(role, { ...role, permissions: [] }, ['operator']),
    ).toBe(true);
  });

  it('hides the universal workspace from role authorization while keeping other dashboard pages', () => {
    const tree = roleGrantMenuTree([
      {
        id: '1',
        name: 'Dashboard',
        type: 'catalog',
        status: 1,
        meta: { title: '概览' },
        children: [
          {
            id: '2',
            name: 'Workspace',
            type: 'menu',
            status: 1,
            path: 'workspace',
            meta: { title: '工作台' },
          },
          {
            id: '3',
            name: 'Analytics',
            type: 'menu',
            status: 1,
            path: 'analytics',
            meta: { title: '分析页' },
          },
        ],
      },
      {
        id: '10',
        name: 'System',
        type: 'catalog',
        status: 1,
        meta: { title: '系统管理' },
        children: [],
      },
    ]);

    expect(tree).toHaveLength(1);
    expect(tree[0]?.name).toBe('Dashboard');
    expect(tree[0]?.children?.map((menu) => menu.name)).toEqual(['Analytics']);
  });
});
