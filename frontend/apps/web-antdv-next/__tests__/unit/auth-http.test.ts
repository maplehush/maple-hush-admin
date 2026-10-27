import { afterEach, describe, expect, it, vi } from 'vitest';

import { authHttp } from '../../src/auth/auth-http';
import { $t } from '../../src/locales';
const input = {
  username: 'test',
  password: 'not-persisted',
  captchaId: 'id',
  captchaCode: 'abcd',
};
function transport(bodies: unknown[]) {
  const fetcher = vi.fn(
    async () =>
      new Response(JSON.stringify(bodies.shift()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
  );
  vi.stubGlobal('fetch', fetcher);
  return fetcher;
}
afterEach(() => vi.unstubAllGlobals());
describe('native authentication contract', () => {
  it('acquires new CSRF after normal login without changing the access response', async () => {
    const result = {
      passwordChangeRequired: false as const,
      accessToken: 'short-access',
    };
    const fetcher = transport([
      { code: 0, data: result },
      { code: 0, data: { token: 'new-csrf' } },
    ]);
    expect(await authHttp.login(input)).toEqual(result);
    expect(fetcher.mock.calls.map(([url]) => url)).toEqual([
      '/api/auth/login',
      '/api/auth/csrf',
    ]);
  });
  it('returns pending credentials only as a dedicated response', async () => {
    const result = {
      passwordChangeRequired: true as const,
      passwordChangeToken: 'pending-only',
    };
    transport([
      { code: 0, data: result },
      { code: 0, data: { token: 'csrf' } },
    ]);
    expect(await authHttp.login(input)).toEqual(result);
  });
  it('rejects a response mixing pending and normal credentials', async () => {
    transport([
      {
        code: 0,
        data: {
          passwordChangeRequired: true,
          accessToken: 'invalid-access',
          passwordChangeToken: 'pending',
        },
      },
    ]);
    await expect(authHttp.login(input)).rejects.toThrow(
      $t('errors.invalidLoginResponse'),
    );
  });
  it('accepts the raw JSON refresh string and sends CSRF plus cookie', async () => {
    const fetcher = transport([
      { code: 0, data: { token: 'csrf' } },
      'renewed-access',
    ]);
    expect(await authHttp.refresh()).toBe('renewed-access');
    expect(fetcher.mock.calls[1]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'X-XSRF-TOKEN': 'csrf' },
    });
  });
  it('logs out without Authorization and reacquires CSRF', async () => {
    const fetcher = transport([
      { code: 0, data: { token: 'before' } },
      { code: 0, data: null },
      { code: 0, data: { token: 'after' } },
    ]);
    await authHttp.logout();
    expect(fetcher.mock.calls.map(([url]) => url)).toEqual([
      '/api/auth/csrf',
      '/api/auth/logout',
      '/api/auth/csrf',
    ]);
    expect(fetcher.mock.calls[1]?.[1]).toMatchObject({
      credentials: 'include',
      headers: { 'X-XSRF-TOKEN': 'before' },
    });
    expect(
      (fetcher.mock.calls[1]?.[1] as RequestInit).headers,
    ).not.toHaveProperty('Authorization');
  });
});
