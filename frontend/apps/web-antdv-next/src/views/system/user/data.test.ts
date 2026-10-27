import { describe, expect, it, vi } from 'vitest';

// 本文件验证表单边界，部门接口由调用方集成测试覆盖，避免加载网络与认证副作用。
vi.mock('#/api/system/dept', () => ({ getDeptList: vi.fn() }));

import {
  avatarUploadErrorKey,
  buildUserPayload,
  canManageProtectedAction,
  departmentIdFromTreeEvent,
  parseRoleIds,
  pendingImageFromAvatar,
  useFormSchema,
} from './data';

describe('用户管理表单边界', () => {
  it('头像表单只绑定新上传成功的凭据，已有头像和空列表不发送绑定', () => {
    expect(pendingImageFromAvatar(undefined)).toBeUndefined();
    expect(pendingImageFromAvatar([])).toBeUndefined();
    expect(
      pendingImageFromAvatar([{ uid: 'existing-avatar', status: 'done' }]),
    ).toBeUndefined();
    expect(
      pendingImageFromAvatar([
        {
          uid: 'new',
          status: 'done',
          response: { uploadId: 'a'.repeat(32), expiresAt: 123 },
        },
      ]),
    ).toEqual({ uploadId: 'a'.repeat(32), expiresAt: 123 });
  });
  it('头像上传中、失败、响应缺失或多文件时不能保存', () => {
    for (const files of [
      [{ uid: 'new', status: 'uploading' }],
      [{ uid: 'new', status: 'error' }],
      [{ uid: 'new', status: 'done' }],
      [{ uid: 'new', status: 'done', response: { uploadId: 'bad' } }],
      [
        { uid: 'a', status: 'done' },
        { uid: 'b', status: 'done' },
      ],
    ]) {
      expect(() => pendingImageFromAvatar(files)).toThrow();
    }
  });
  it('头像上传错误按后端错误码给出可操作提示，不把存储故障说成换图片', () => {
    expect(
      avatarUploadErrorKey({
        response: { data: { error: 'DEPENDENCY_UNAVAILABLE' } },
      }),
    ).toBe('avatarStorageUnavailable');
    expect(
      avatarUploadErrorKey({
        response: { data: { error: 'INVALID_ARGUMENT' } },
      }),
    ).toBe('avatarContentInvalid');
    expect(avatarUploadErrorKey(new Error('Network Error'))).toBe(
      'avatarUpload',
    );
  });
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
  it('新增用户可以不分配角色', () => {
    expect(
      buildUserPayload(
        {
          username: 'alice',
          name: 'Alice',
          deptId: '2',
          remark: '',
          status: 1,
          roleIds: [],
        },
        false,
      ),
    ).toMatchObject({ username: 'alice', roleIds: [] });
  });
  it('角色选择和手动输入均允许留空，仍校验已输入角色的数量', () => {
    for (const canListRole of [true, false]) {
      const field = useFormSchema({ canAssign: true, canListRole }).find(
        (item) => item.fieldName === 'roleIds',
      );
      if (!field || !('rules' in field))
        throw new Error('角色字段缺少校验配置');
      const rules = field.rules as {
        safeParse: (value: unknown) => { success: boolean };
      };
      expect(rules.safeParse(undefined).success).toBe(true);
      expect(rules.safeParse(canListRole ? [] : '').success).toBe(true);
      if (canListRole)
        expect(
          rules.safeParse(Array.from({ length: 101 }, () => '2')).success,
        ).toBe(false);
    }
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
