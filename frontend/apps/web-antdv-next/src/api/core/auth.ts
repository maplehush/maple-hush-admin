import type { LoginInput, LoginResponse } from '#/auth/session-coordinator';

import { requestClient } from '#/api/request';
import { authHttp } from '#/auth/auth-http';
import { session } from '#/auth/session';
export namespace AuthApi {
  export type LoginParams = LoginInput;
  export type LoginResult = LoginResponse;
}
export const loginApi = (data: LoginInput) => session.login(data);
export const logoutApi = () => session.logout();
export const getCaptchaApi = authHttp.captcha;
export const changeInitialPasswordApi = authHttp.initialPassword;
export function getAccessCodesApi() {
  return requestClient.get<string[]>('/auth/codes');
}
