import { describe, expect, it } from 'vitest';

import enDemos from './langs/en-US/demos.json';
import enPage from './langs/en-US/page.json';
import enSystem from './langs/en-US/system.json';
import zhDemos from './langs/zh-CN/demos.json';
import zhPage from './langs/zh-CN/page.json';
import zhSystem from './langs/zh-CN/system.json';

function messageShape(value: unknown): unknown {
  if (typeof value !== 'object' || value === null) {
    return typeof value;
  }
  return Object.fromEntries(
    Object.entries(value)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, child]) => [key, messageShape(child)]),
  );
}

describe('application locale messages', () => {
  it('keeps Simplified Chinese and English page/system message keys in sync', () => {
    expect(messageShape(enPage)).toEqual(messageShape(zhPage));
    expect(messageShape(enSystem)).toEqual(messageShape(zhSystem));
    expect(messageShape(enDemos)).toEqual(messageShape(zhDemos));
  });

  it('translates the Vben documentation title in both supported languages', () => {
    expect(zhDemos.vben.document).toBe('文档');
    expect(enDemos.vben.document).toBe('Document');
  });
});
