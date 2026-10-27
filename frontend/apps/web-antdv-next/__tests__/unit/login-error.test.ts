import { describe, expect, it } from 'vitest';

import { AuthHttpError } from '../../src/auth/auth-http';
import { SessionCoordinationError } from '../../src/auth/browser-session';
import { resolveLoginFailure } from '../../src/auth/login-error';

describe('login error messages', () => {
  it('keeps the backend credential error without confusing it with browser errors', () => {
    expect(resolveLoginFailure(new AuthHttpError(401, '账号或密码错误'))).toBe(
      '账号或密码错误',
    );
  });
  it('keeps specific captcha and dependency failures', () => {
    expect(resolveLoginFailure(new AuthHttpError(400, '验证码无效'))).toBe('验证码无效');
    expect(resolveLoginFailure(new AuthHttpError(503, '服务暂不可用'))).toBe('服务暂不可用');
  });
  it('does not report a browser exception as incorrect credentials or expose internals', () => {
    const result = resolveLoginFailure(new TypeError('crypto.randomUUID is not a function'));
    expect(result).toBe('page.auth.errors.loginProcessing');
    expect(result).not.toContain('密码');
    expect(result).not.toContain('randomUUID');
  });
  it('explains blocked browser storage without blaming credentials', () => {
    expect(resolveLoginFailure(new SessionCoordinationError()))
      .toBe('page.auth.errors.sessionStorage');
  });
});
