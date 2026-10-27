import { describe, expect, it } from 'vitest';

import { colorText, dictItemPayload } from '../../src/views/system/dict/data';
describe('dictionary input and readable labels', () => {
  const form = {
    dictTypeId: '7',
    label: ' Open ',
    value: ' 1 ',
    color: '#AABBCC',
    sort: 0,
    status: 0,
    defaultItem: true,
    remark: '',
  };
  it('submits only declared fields, preserves disabled/default independently, normalizes color', () => {
    expect(
      dictItemPayload({ ...form, id: 'not-an-input' } as typeof form),
    ).toEqual({ ...form, label: 'Open', value: '1', color: '#aabbcc' });
  });
  it('supports clearing color and rejects CSS injection or short HEX', () => {
    expect(dictItemPayload({ ...form, color: '' }).color).toBeNull();
    expect(() => dictItemPayload({ ...form, color: '#fff' })).toThrow();
    expect(() =>
      dictItemPayload({ ...form, color: 'red;background:url(x)' }),
    ).toThrow();
  });
  it('uses readable foreground for light/dark colors and plain style without color', () => {
    expect(colorText('#ffffff')).toBe('#000000');
    expect(colorText('#000000')).toBe('#ffffff');
    expect(colorText(null)).toBeUndefined();
  });
});
