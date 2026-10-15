import { describe, expect, it } from 'vitest';

import {
  buildUserPayload,
  canManageProtectedAction,
  departmentIdFromTreeEvent,
  parseRoleIds,
} from './form-policy';

describe('用户管理表单边界', () => {
  it('部门树选择解包节点中的字符串ID，清空时不发送筛选参数', () => {
    expect(
      departmentIdFromTreeEvent({
        value: { id: '9007199254740993', name: '研发部' },
        level: 1,
        index: 0,
      }),
    ).toBe('9007199254740993');
    expect(departmentIdFromTreeEvent(undefined)).toBeUndefined();
    expect(departmentIdFromTreeEvent(null)).toBeUndefined();
    expect(() => departmentIdFromTreeEvent({ value: { id: {} } })).toThrow();
    expect(() => departmentIdFromTreeEvent({ value: { id: '1e3' } })).toThrow();
  });
  it('资料更新剔除账号、角色和任意头像 URL，保留清空字段', () => {
    expect(
      buildUserPayload(
        {
          username: 'immutable',
          name: ' Alice ',
          deptId: '',
          remark: '',
          status: 1,
          roleIds: ['2'],
          avatar: 'https://bad.test',
        },
        true,
      ),
    ).toEqual({ name: 'Alice', deptId: null, remark: '', status: 1 });
  });
  it('新增只提交真实上传凭据，过期凭据拒绝提交', () => {
    const form = {
      username: 'alice',
      name: 'Alice',
      deptId: '2',
      remark: '',
      status: 1 as const,
      roleIds: ['3'],
    };
    expect(
      buildUserPayload(
        form,
        false,
        { uploadId: 'a'.repeat(32), expiresAt: 200 },
        100,
      ),
    ).toEqual({ ...form, avatarUploadId: 'a'.repeat(32) });
    expect(() =>
      buildUserPayload(
        form,
        false,
        { uploadId: 'a'.repeat(32), expiresAt: 100 },
        100,
      ),
    ).toThrow();
  });
  it('明确超管或未知身份不能操作受保护入口，普通账户可以', () => {
    expect(canManageProtectedAction({ superAdmin: true })).toBe(false);
    expect(canManageProtectedAction({})).toBe(false);
    expect(canManageProtectedAction({ superAdmin: false })).toBe(true);
  });
  it('角色 ID 保持字符串精度、去重并拒绝非法输入', () => {
    expect(parseRoleIds('9007199254740993, 2,2')).toEqual([
      '9007199254740993',
      '2',
    ]);
    for (const value of ['1e3', '0', '-1', '9223372036854775808', '2.1'])
      expect(() => parseRoleIds(value)).toThrow();
  });
});
