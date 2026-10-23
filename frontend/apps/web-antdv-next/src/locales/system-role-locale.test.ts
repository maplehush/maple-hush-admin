import { describe, expect, it } from 'vitest';

import en from './langs/en-US/system.json';
import zh from './langs/zh-CN/system.json';

describe('role status confirmation translations', () => {
  it('defines title, interpolated confirmation, and success in both locales', () => {
    for (const locale of [zh, en]) {
      expect(locale.role.statusChangeTitle).toBeTruthy();
      expect(locale.role.statusChangeConfirm).toContain('{name}');
      expect(locale.role.statusChangeConfirm).toContain('{status}');
      expect(locale.role.statusChangeSuccess).toBeTruthy();
    }
  });
});
