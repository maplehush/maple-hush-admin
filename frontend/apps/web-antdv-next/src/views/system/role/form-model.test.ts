import { describe, expect, it } from 'vitest';

import { affectsCurrentRoles, rolePayload } from './form-model';

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
});
