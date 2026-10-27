import { describe, expect, it, vi } from 'vitest';

import { SessionCoordinationError } from '../../src/auth/browser-session';
import {
  SessionCoordinator,
  type SessionPorts,
  type SessionState,
} from '../../src/auth/session-coordinator';

const credentials = {
  username: 'alice',
  password: 'input-only',
  captchaId: 'id',
  captchaCode: 'ABCD',
};
function fixture() {
  let state: SessionState = {
    epoch: 'session-one',
    revision: 1,
    token: 'old-access',
  };
  let tail = Promise.resolve();
  const ports: SessionPorts = {
    available: () => true,
    lock: async (operation) => {
      const previous = tail;
      let release!: () => void;
      tail = new Promise<void>((resolve) => {
        release = resolve;
      });
      await previous;
      try {
        return await operation();
      } finally {
        release();
      }
    },
    read: () => state,
    write: (next) => {
      state = next;
    },
    random: () => 'new-epoch',
    transport: {
      login: vi.fn(async () => ({
        passwordChangeRequired: false as const,
        accessToken: 'signed-in',
      })),
      logout: vi.fn(async () => {}),
      refresh: vi.fn(async () => {
        await Promise.resolve();
        return 'new-access';
      }),
    },
  };
  return { ports, coordinator: new SessionCoordinator(ports) };
}

describe('session coordination', () => {
  it('invalidates the old account when login may have changed its refresh cookie before failing', async () => {
    const { ports, coordinator } = fixture();
    ports.transport.login = vi.fn(async () => {
      throw Object.assign(new Error('post-login csrf unavailable'), {
        status: 503,
      });
    });
    await expect(coordinator.login(credentials)).rejects.toThrow();
    expect(coordinator.state.token).toBeNull();
    await expect(
      coordinator.refresh('old-access', 'session-one'),
    ).rejects.toThrow();
    expect(ports.transport.refresh).not.toHaveBeenCalled();
  });
  it('does not log out a newly signed-in account after waiting for the lock', async () => {
    const { ports, coordinator } = fixture();
    ports.lock = async (operation) => {
      ports.write({
        epoch: 'other-account',
        revision: 0,
        token: 'other-access',
      });
      return operation();
    };
    await expect(coordinator.logout()).rejects.toThrow();
    expect(ports.transport.logout).not.toHaveBeenCalled();
    expect(coordinator.state.token).toBe('other-access');
  });
  it('merges concurrent 401 refreshes and reuses the resulting token', async () => {
    const { ports, coordinator } = fixture();
    expect(
      await Promise.all(
        Array.from({ length: 8 }, () =>
          coordinator.refresh('old-access', 'session-one'),
        ),
      ),
    ).toEqual(Array(8).fill('new-access'));
    expect(ports.transport.refresh).toHaveBeenCalledTimes(1);
    expect(await coordinator.refresh('old-access', 'session-one')).toBe(
      'new-access',
    );
    expect(ports.transport.refresh).toHaveBeenCalledTimes(1);
  });
  it('serializes two tabs using the shared state and one refresh', async () => {
    const { ports, coordinator } = fixture();
    const anotherTab = new SessionCoordinator(ports);
    expect(
      await Promise.all([
        coordinator.refresh('old-access', 'session-one'),
        anotherTab.refresh('old-access', 'session-one'),
      ]),
    ).toEqual(['new-access', 'new-access']);
    expect(ports.transport.refresh).toHaveBeenCalledTimes(1);
  });
  it('does not retry an old account request after a new login', async () => {
    const { ports, coordinator } = fixture();
    await coordinator.login(credentials);
    await expect(
      coordinator.refresh('old-access', 'session-one'),
    ).rejects.toThrow();
    expect(ports.transport.refresh).not.toHaveBeenCalled();
    expect(coordinator.state.token).toBe('signed-in');
  });
  it('never promotes password-change credentials to access or persistence', async () => {
    const { ports, coordinator } = fixture();
    ports.transport.login = vi.fn(async () => ({
      passwordChangeRequired: true,
      passwordChangeToken: 'short-secret',
    }));
    const result = await coordinator.login(credentials);
    expect(result.passwordChangeRequired).toBe(true);
    expect(coordinator.state.token).toBeNull();
    expect(JSON.stringify(coordinator.state)).not.toContain('short-secret');
  });
  it('settles every waiter and clears the session on CSRF refresh failure', async () => {
    const { ports, coordinator } = fixture();
    ports.transport.refresh = vi.fn(async () => {
      throw Object.assign(new Error('csrf'), { status: 403 });
    });
    const results = await Promise.allSettled([
      coordinator.refresh('old-access', 'session-one'),
      coordinator.refresh('old-access', 'session-one'),
    ]);
    expect(results.every((result) => result.status === 'rejected')).toBe(true);
    expect(ports.transport.refresh).toHaveBeenCalledTimes(1);
    expect(coordinator.state.token).toBeNull();
    await expect(
      coordinator.refresh('old-access', 'session-one'),
    ).rejects.toThrow();
    expect(ports.transport.refresh).toHaveBeenCalledTimes(1);
  });
  it('preserves a session on an explicit dependency-unavailable result', async () => {
    const { ports, coordinator } = fixture();
    ports.transport.refresh = vi.fn(async () => {
      throw Object.assign(new Error('dependency'), { status: 503 });
    });
    await expect(
      coordinator.refresh('old-access', 'session-one'),
    ).rejects.toThrow();
    expect(coordinator.state.token).toBe('old-access');
  });
  it('does not invalidate another tab while waiting for browser coordination', async () => {
    const { ports, coordinator } = fixture();
    ports.lock = async () => { throw new SessionCoordinationError(); };
    await expect(coordinator.refresh('old-access', 'session-one'))
      .rejects.toBeInstanceOf(SessionCoordinationError);
    expect(ports.transport.refresh).not.toHaveBeenCalled();
    expect(coordinator.state.token).toBe('old-access');
  });
  it('requires login without submitting refresh when coordination is unavailable', async () => {
    const { ports, coordinator } = fixture();
    ports.available = () => false;
    await expect(
      coordinator.refresh('old-access', 'session-one'),
    ).rejects.toThrow();
    expect(ports.transport.refresh).not.toHaveBeenCalled();
    expect(coordinator.state.token).toBeNull();
  });
  it('clears local state even when logout fails and does not need an access token', async () => {
    const { ports, coordinator } = fixture();
    ports.transport.logout = vi.fn(async () => {
      throw new Error('network');
    });
    await expect(coordinator.logout()).rejects.toThrow();
    expect(coordinator.state.token).toBeNull();
    expect(ports.transport.logout).toHaveBeenCalledTimes(1);
  });
  it('does not resurrect a session when logout happens during a refresh response', async () => {
    const { ports, coordinator } = fixture();
    ports.transport.refresh = vi.fn(async () => {
      ports.write({ epoch: 'logged-out', revision: 0, token: null });
      return 'late-token';
    });
    await expect(
      coordinator.refresh('old-access', 'session-one'),
    ).rejects.toThrow();
    expect(coordinator.state.token).toBeNull();
  });
});
