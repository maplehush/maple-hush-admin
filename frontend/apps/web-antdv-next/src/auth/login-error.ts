import { $t } from '#/locales';

import { AuthHttpError } from './auth-http';
import { SessionCoordinationError } from './browser-session';
import { SessionChangedError } from './session-coordinator';

/** 后端错误已按稳定错误码翻译；浏览器或跳转异常不伪装为凭据错误。 */
export function resolveLoginFailure(error: unknown): string {
  if (error instanceof AuthHttpError) {
    return error.message;
  }
  if (error instanceof SessionCoordinationError) {
    return $t('page.auth.errors.sessionStorage');
  }
  if (error instanceof SessionChangedError) {
    return $t('page.auth.errors.sessionChanged');
  }
  return $t('page.auth.errors.loginProcessing');
}
