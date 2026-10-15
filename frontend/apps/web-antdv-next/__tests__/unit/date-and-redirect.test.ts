import { describe, expect, it } from 'vitest';

import {
  parseServerDateTime,
  SERVER_DATE_TIME_FORMAT,
} from '../../src/utils/date-time';
import { safeRedirect } from '../../src/utils/redirect';
describe('server wall clock and internal redirects', () => {
  it('keeps the server date and clock without adding eight hours', () => {
    expect(
      parseServerDateTime('2026-10-14 10:20:30')?.format(
        SERVER_DATE_TIME_FORMAT,
      ),
    ).toBe('2026-10-14 10:20:30');
    expect(parseServerDateTime('2026-02-30 10:20:30')).toBeNull();
    expect(parseServerDateTime('2026-10-14T10:20:30Z')).toBeNull();
  });
  it.each([
    'https://example.com',
    '//example.com',
    '/\\example.com',
    '/auth/login',
    undefined,
  ])('rejects unsafe redirect %s', (value) => {
    expect(safeRedirect(value)).toBe('/dashboard/workspace');
  });
  it('preserves internal filters', () =>
    expect(safeRedirect('/system/user?name=a%20b')).toBe(
      '/system/user?name=a%20b',
    ));
});
