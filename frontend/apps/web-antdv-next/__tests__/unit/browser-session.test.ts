import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  createSessionEpoch,
  SessionCoordinationError,
  withSessionLock,
} from '../../src/auth/browser-session';

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('browser session capabilities', () => {
  it('creates independent session epochs without randomUUID or Web Locks', () => {
    const getRandomValues = crypto.getRandomValues.bind(crypto);
    vi.stubGlobal('crypto', { getRandomValues });
    const epochs = Array.from({ length: 100 }, () => createSessionEpoch());
    expect(epochs.every((epoch) => /^[a-f\d]{32}$/.test(epoch))).toBe(true);
    expect(new Set(epochs).size).toBe(100);
  });
  it('never submits an operation if the browser denies IndexedDB', async () => {
    vi.stubGlobal('indexedDB', {
      open: () => { throw new DOMException('blocked', 'SecurityError'); },
    });
    const operation = vi.fn(async () => 'must-not-run');
    await expect(withSessionLock(operation)).rejects.toBeInstanceOf(SessionCoordinationError);
    expect(operation).not.toHaveBeenCalled();
  });
  it('times out a blocked database open and closes a late connection without submitting', async () => {
    vi.useFakeTimers();
    const database = { close: vi.fn() };
    const request: { onsuccess?: () => void; result: typeof database } = { result: database };
    vi.stubGlobal('indexedDB', { open: () => request });
    const operation = vi.fn(async () => 'must-not-run');
    const assertion = expect(withSessionLock(operation)).rejects.toBeInstanceOf(SessionCoordinationError);
    await vi.advanceTimersByTimeAsync(7_001);
    await assertion;
    request.onsuccess?.();
    expect(database.close).toHaveBeenCalledOnce();
    expect(operation).not.toHaveBeenCalled();
  });
});
