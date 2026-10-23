import zhLocale from 'antdv-next/dist/locale/zh_CN';
import { describe, expect, it, vi } from 'vitest';

import { antdLocale, setupI18n } from './index';

const mocks = vi.hoisted(() => ({
  locale: 'unsupported',
  setupI18n: vi.fn(),
  updatePreferences: vi.fn(),
}));

vi.mock('@vben/locales', () => ({
  $t: (key: string) => key,
  loadLocalesMapFromDir: () => ({}),
  setupI18n: mocks.setupI18n,
}));

vi.mock('@vben/preferences', () => ({
  preferences: {
    app: {
      get locale() {
        return mocks.locale;
      },
    },
  },
  updatePreferences: mocks.updatePreferences,
}));

describe('application locale', () => {
  it('resets Antdv locale when a saved language is unsupported', async () => {
    await setupI18n({} as Parameters<typeof setupI18n>[0]);
    const setupOptions = mocks.setupI18n.mock.calls[0]?.[1];
    expect(setupOptions).toBeDefined();
    expect(setupOptions!.defaultLocale).toBe('zh-CN');
    expect(mocks.updatePreferences).toHaveBeenCalledWith({
      app: { locale: 'zh-CN' },
    });
    const loadMessages = setupOptions!.loadMessages;

    await loadMessages('zh-CN');
    await loadMessages('unsupported');

    expect(antdLocale.value).toEqual(zhLocale);
  });
});
