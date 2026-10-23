interface ErrorBody {
  error?: unknown;
  message?: unknown;
}

const errorKeys: Record<string, string> = {
  BAD_REQUEST: 'invalidRequest',
  INVALID_ARGUMENT: 'invalidArgument',
  CAPTCHA_INVALID: 'captchaInvalid',
  UNAUTHENTICATED: 'unauthenticated',
  FORBIDDEN: 'forbidden',
  CSRF_INVALID: 'csrfInvalid',
  NOT_FOUND: 'notFound',
  CONFLICT: 'conflict',
  RATE_LIMITED: 'rateLimited',
  DEPENDENCY_UNAVAILABLE: 'unavailable',
  INTERNAL_ERROR: 'internal',
};

/**
 * 中文保留业务细节；其他语言按稳定错误码翻译，未知错误使用本地化兜底。
 */
export function resolveApiErrorMessage(
  body: ErrorBody | null | undefined,
  locale: string,
  translate: (key: string) => string,
  login = false,
): string {
  if (
    locale.startsWith('zh') &&
    typeof body?.message === 'string' &&
    body.message.trim()
  ) {
    return body.message;
  }
  const key =
    login && body?.error === 'UNAUTHENTICATED'
      ? 'invalidCredentials'
      : Object.hasOwn(errorKeys, String(body?.error))
        ? errorKeys[String(body?.error)]!
        : 'requestFailed';
  return translate(`errors.${key}`);
}
