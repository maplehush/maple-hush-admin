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
    expect(() => profilePayload({ realName: '  ' })).toThrow(
      'page.profile.errors.nameRequired',
    );
    expect(() =>
      profilePayload({ realName: '张三', introduction: 'a'.repeat(501) }),
    ).toThrow('page.profile.errors.introductionLength');
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
    ).toThrow('page.profile.errors.passwordMismatch');
  });

  it('checks the backend password length and byte limits before sending', () => {
    expect(() =>
      passwordPayload({
        oldPassword: 'old-pass-123',
        newPassword: '中'.repeat(25),
        confirmPassword: '中'.repeat(25),
      }),
    ).toThrow('page.profile.errors.passwordBytes');
  });
});
