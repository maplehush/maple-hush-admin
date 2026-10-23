import type { LoginInput, LoginResponse } from './session-coordinator';

import { preferences } from '@vben/preferences';

import { resolveApiErrorMessage } from '#/api/error-message';
import { $t } from '#/locales';
export class AuthHttpError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
/** 认证传输独立于业务拦截器，避免 CSRF、刷新失败形成循环。 */
async function send<T>(
  path: string,
  init: RequestInit = {},
  raw = false,
): Promise<T> {
  const response = await fetch(`/api/auth/${path}`, {
    ...init,
    credentials: 'include',
    cache: 'no-store',
    headers: { 'Content-Type': 'application/json', ...init.headers },
    signal: AbortSignal.timeout(15_000),
  });
  const body = await response.json().catch(() => {
    throw new AuthHttpError(response.status, $t('errors.requestFailed'));
  });
  if (!response.ok || (!raw && body?.code !== 0)) {
    throw new AuthHttpError(
      response.status,
      resolveApiErrorMessage(
        body,
        preferences.app.locale,
        $t,
        path === 'login',
      ),
    );
  }
  return raw ? body : body.data;
}
async function csrfHeaders() {
  const data = await send<{ token: string }>('csrf');
  return { 'X-XSRF-TOKEN': data.token };
}
export const authHttp = {
  captcha: () => send<{ captchaId: string; imageBase64: string }>('captcha'),
  async login(data: LoginInput) {
    const result = await send<LoginResponse>('login', {
      method: 'POST',
      body: JSON.stringify(data),
    });
    if (
      !result ||
      (result.passwordChangeRequired !== true &&
        result.passwordChangeRequired !== false) ||
      (result.passwordChangeRequired
        ? typeof result.passwordChangeToken !== 'string' ||
          !result.passwordChangeToken ||
          'accessToken' in result
        : typeof result.accessToken !== 'string' ||
          !result.accessToken ||
          'passwordChangeToken' in result)
    ) {
      throw new Error($t('errors.invalidLoginResponse'));
    }
    // 后端登录成功会清理旧 CSRF Cookie，立即领取新凭据。
    await csrfHeaders();
    return result;
  },
  initialPassword: (token: string, newPassword: string) =>
    send<void>('initial-password', {
      method: 'PUT',
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify({ newPassword }),
    }),
  async refresh() {
    const token = await send<unknown>(
      'refresh',
      { method: 'POST', headers: await csrfHeaders() },
      true,
    );
    if (typeof token !== 'string' || !token) {
      throw new Error($t('errors.invalidRefreshResponse'));
    }
    return token;
  },
  async logout() {
    await send<void>('logout', {
      method: 'POST',
      headers: await csrfHeaders(),
    });
    await csrfHeaders();
  },
};
