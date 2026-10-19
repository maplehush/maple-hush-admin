import { describe, expect, it } from 'vitest';

import { passwordPayload, profilePayload } from './model';

describe('current user forms', () => {
  it('only submits editable profile fields', () => {
    expect(
      profilePayload({
        realName: '  张三  ',
        introduction: '  简介  ',
        username: 'admin',
        roles: ['super_admin'],
        avatar: 'https://example.com/other.png',
      }),
    ).toEqual({ realName: '张三', introduction: '简介' });
  });

  it('rejects an empty name and overlong introduction', () => {
    expect(() => profilePayload({ realName: '  ' })).toThrow('姓名');
    expect(() =>
      profilePayload({ realName: '张三', introduction: 'a'.repeat(501) }),
    ).toThrow('个人简介');
  });

  it('submits only the two passwords after confirming the new password', () => {
    expect(
      passwordPayload({
        oldPassword: 'old-pass-123',
        newPassword: 'new-pass-456',
        confirmPassword: 'new-pass-456',
      }),
    ).toEqual({ oldPassword: 'old-pass-123', newPassword: 'new-pass-456' });
    expect(() =>
      passwordPayload({
        oldPassword: 'old-pass-123',
        newPassword: 'new-pass-456',
        confirmPassword: 'different',
      }),
    ).toThrow('两次输入');
  });

  it('checks the backend password length and byte limits before sending', () => {
    expect(() =>
      passwordPayload({
        oldPassword: 'old-pass-123',
        newPassword: '中'.repeat(25),
        confirmPassword: '中'.repeat(25),
      }),
    ).toThrow('72 字节');
  });
});
