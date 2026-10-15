export interface SessionState {
  epoch: string;
  revision: number;
  token: null | string;
}
export interface LoginInput {
  username: string;
  password: string;
  captchaId: string;
  captchaCode: string;
}
export type LoginResponse =
  | { passwordChangeRequired: false; accessToken: string }
  | { passwordChangeRequired: true; passwordChangeToken: string };
export interface SessionPorts {
  available: () => boolean;
  lock: <T>(operation: () => Promise<T>) => Promise<T>;
  read: () => SessionState;
  write: (state: SessionState) => void;
  random: () => string;
  transport: {
    login: (data: LoginInput) => Promise<LoginResponse>;
    logout: () => Promise<void>;
    refresh: () => Promise<string>;
  };
}
export class SessionChangedError extends Error {}
/** 同页合并请求，跨页通过共享锁和会话代次保护单次轮换的 refresh cookie。 */
export class SessionCoordinator {
  get state(): SessionState {
    return this.ports.read();
  }
  private pending?: Promise<string>;
  constructor(readonly ports: SessionPorts) {}
  invalidate(epoch = this.state.epoch) {
    if (this.state.epoch === epoch) {
      this.ports.write({
        epoch: this.ports.random(),
        revision: 0,
        token: null,
      });
    }
  }
  async login(data: LoginInput): Promise<LoginResponse> {
    const operation = async () => {
      const epoch = this.state.epoch;
      let result: LoginResponse;
      try {
        result = await this.ports.transport.login(data);
      } catch (error) {
        // Cookie 可能已被服务端更换（例如随后获取 CSRF 失败），不可保留旧账号身份。
        this.invalidate(epoch);
        throw error;
      }
      if (this.state.epoch !== epoch) {
        throw new SessionChangedError('登录状态已变化，请重试');
      }
      this.ports.write({
        epoch: this.ports.random(),
        revision: 0,
        token: result.passwordChangeRequired ? null : result.accessToken,
      });
      return result;
    };
    return this.ports.available() ? this.ports.lock(operation) : operation();
  }
  async logout(): Promise<void> {
    const epoch = this.state.epoch;
    const operation = async () => {
      if (this.state.epoch !== epoch) {
        throw new SessionChangedError('登录状态已变化，请重新操作');
      }
      this.invalidate(epoch);
      await this.ports.transport.logout();
    };
    try {
      await (this.ports.available() ? this.ports.lock(operation) : operation());
    } catch (error) {
      this.invalidate(epoch);
      throw error;
    }
  }
  async refresh(failed: string, epoch: string): Promise<string> {
    const current = () => {
      const state = this.state;
      if (state.epoch !== epoch || !state.token) {
        throw new SessionChangedError('登录已失效，请重新登录');
      }
      return state;
    };
    if (current().token !== failed) {
      return current().token!;
    }
    if (this.pending) {
      return this.pending;
    }
    this.pending = (async () => {
      try {
        if (!this.ports.available()) {
          throw new Error('浏览器无法协调会话，请重新登录');
        }
        return await this.ports.lock(async () => {
          if (current().token !== failed) {
            return current().token!;
          }
          const token = await this.ports.transport.refresh();
          const state = current();
          this.ports.write({ ...state, revision: state.revision + 1, token });
          return token;
        });
      } catch (error) {
        if ((error as { status?: number }).status !== 503) {
          this.invalidate(epoch);
        }
        throw error;
      } finally {
        this.pending = undefined;
      }
    })();
    return this.pending;
  }
}
