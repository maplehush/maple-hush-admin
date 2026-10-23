import { describe, expect, it } from 'vitest';

import { resolveApiErrorMessage } from './error-message';

describe('localized API errors', () => {
  const translate = (key: string) => `translated:${key}`;
  it('preserves Chinese business details', () => {
    expect(
      resolveApiErrorMessage(
        { error: 'CONFLICT', message: '字典编码已存在' },
        'zh-CN',
        translate,
      ),
    ).toBe('字典编码已存在');
  });
  it('translates stable codes in English instead of showing a Chinese response', () => {
    expect(
      resolveApiErrorMessage(
        { error: 'RATE_LIMITED', message: '次数已达限制' },
        'en-US',
        translate,
      ),
    ).toBe('translated:errors.rateLimited');
    expect(
      resolveApiErrorMessage(
        { error: 'UNAUTHENTICATED' },
        'en-US',
        translate,
        true,
      ),
    ).toBe('translated:errors.invalidCredentials');
  });
  it('uses a localized fallback for unknown responses, network errors and inherited property names', () => {
    for (const body of [
      undefined,
      { error: 'UNKNOWN' },
      { error: '__proto__' },
      { error: 'constructor' },
    ]) {
      expect(resolveApiErrorMessage(body, 'en-US', translate)).toBe(
        'translated:errors.requestFailed',
      );
    }
  });
});
