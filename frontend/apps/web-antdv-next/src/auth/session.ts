import { authHttp } from './auth-http';
import { createSessionEpoch, SessionCoordinationError, withSessionLock } from './browser-session';
import { SessionCoordinator, type SessionState } from './session-coordinator';
export const SESSION_KEY = 'maple-hush-admin:auth:v1';
const empty: SessionState = { epoch: 'anonymous', revision: 0, token: null };
const listeners = new Set<(state: SessionState, remote: boolean) => void>();
let fallback = empty;
let storageAvailable = true;
const channel =
  typeof BroadcastChannel === 'undefined'
    ? undefined
    : new BroadcastChannel(SESSION_KEY);
function read(): SessionState {
  if (!storageAvailable) {
    return fallback;
  }
  try {
    const value = JSON.parse(localStorage.getItem(SESSION_KEY) || 'null');
    if (
      value &&
      typeof value.epoch === 'string' &&
      Number.isInteger(value.revision) &&
      (value.token === null || typeof value.token === 'string')
    ) {
      return value;
    }
    return empty;
  } catch {
    storageAvailable = false;
    return fallback;
  }
}
function publish(state: SessionState, remote = false) {
  for (const listener of listeners) {
    listener(state, remote);
  }
}
export const session = new SessionCoordinator({
  available: () => storageAvailable && typeof indexedDB !== 'undefined',
  read,
  random: createSessionEpoch,
  write: (state) => {
    const previous = read();
    fallback = state;
    try {
      localStorage.setItem(SESSION_KEY, JSON.stringify(state));
    } catch {
      storageAvailable = false;
      fallback = { epoch: createSessionEpoch(), revision: 0, token: null };
      try {
        localStorage.removeItem(SESSION_KEY);
      } catch {
        /* 存储被禁用时由频道通知其他页面失效。 */
      }
      publish(fallback);
      channel?.postMessage({ invalidatedEpoch: previous.epoch });
      throw new SessionCoordinationError();
    }
    publish(state);
    channel?.postMessage('changed');
  },
  lock: withSessionLock,
  transport: authHttp,
});
channel?.addEventListener('message', (event) => {
  if (typeof event.data?.invalidatedEpoch === 'string') {
    session.invalidate(event.data.invalidatedEpoch);
  }
  publish(read(), true);
});
window.addEventListener('storage', (event) => {
  if (event.key === SESSION_KEY || event.key === null) {
    publish(read(), true);
  }
});
export function subscribeSession(
  listener: (state: SessionState, remote: boolean) => void,
) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
