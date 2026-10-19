import enLocale from 'antdv-next/dist/locale/en_US';
import { describe, expect, it, vi } from 'vitest';

import { antdLocale, setupI18n } from './index';

const mocks = vi.hoisted(() => ({
  setupI18n: vi.fn(),
}));

vi.mock('@vben/locales', () => ({
  $t: (key: string) => key,
  loadLocalesMapFromDir: () => ({}),
  setupI18n: mocks.setupI18n,
}));

vi.mock('@vben/preferences', () => ({
  preferences: { app: { locale: 'zh-CN' } },
}));

describe('application locale', () => {
  it('resets Antdv locale when a saved language is unsupported', async () => {
    await setupI18n({} as Parameters<typeof setupI18n>[0]);
    const setupOptions = mocks.setupI18n.mock.calls[0]?.[1];
    expect(setupOptions).toBeDefined();
    const loadMessages = setupOptions!.loadMessages;

    await loadMessages('zh-CN');
    await loadMessages('unsupported');

    expect(antdLocale.value).toEqual(enLocale);
  });
});
