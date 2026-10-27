const DATABASE = 'maple-hush-admin:auth-coordination:v1';
const STORE = 'authentication';

/** 浏览器存储不可用或协调等待超时，不能当作账号密码错误。 */
export class SessionCoordinationError extends Error {
  constructor() {
    super('Browser session coordination is unavailable');
    this.name = 'SessionCoordinationError';
  }
}

/** 会话代次不是登录凭据；getRandomValues 在普通 HTTP 下也可用。 */
export function createSessionEpoch(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE, 1);
    let expired = false;
    const timer = window.setTimeout(() => {
      expired = true;
      reject(new SessionCoordinationError());
    }, 7_000);
    request.onupgradeneeded = () => {
      request.result.createObjectStore(STORE);
    };
    request.onerror = () => {
      window.clearTimeout(timer);
      reject(new SessionCoordinationError());
    };
    request.onsuccess = () => {
      window.clearTimeout(timer);
      if (expired) {
        request.result.close();
      } else {
        resolve(request.result);
      }
    };
  });
}

/**
 * 同源页面对同一仓库的 readwrite 事务由浏览器串行执行，无需 Web Locks。
 * 事务只用于排队，不保存凭据；网络操作期间维持一个待完成的读取请求，
 * 避免事务提前提交。页面关闭时浏览器释放事务，不使用定时过期租约。
 */
export async function withSessionLock<T>(
  operation: () => Promise<T>,
): Promise<T> {
  let database: IDBDatabase;
  try {
    database = await openDatabase();
  } catch {
    throw new SessionCoordinationError();
  }
  try {
    return await new Promise<T>((resolve, reject) => {
      const transaction = database.transaction(STORE, 'readwrite');
      const store = transaction.objectStore(STORE);
      let started = false;
      let finished = false;
      let failed = false;
      let result: T;
      let failure: unknown;
      // 等待范围覆盖另一个页面的两次认证 HTTP 请求；获取事务后取消计时。
      const timer = window.setTimeout(() => {
        transaction.abort();
      }, 45_000);
      transaction.onabort = () => {
        window.clearTimeout(timer);
        reject(new SessionCoordinationError());
      };
      transaction.oncomplete = () => {
        window.clearTimeout(timer);
        if (!finished) {
          reject(new SessionCoordinationError());
        } else if (failed) {
          reject(failure);
        } else {
          resolve(result);
        }
      };
      function keepAlive() {
        store.get('mutex').onsuccess = () => {
          if (!started) {
            started = true;
            window.clearTimeout(timer);
            void Promise.resolve().then(operation).then(
              (value) => {
                result = value;
                finished = true;
              },
              (error: unknown) => {
                failure = error;
                failed = true;
                finished = true;
              },
            );
          }
          if (!finished) {
            keepAlive();
          }
        };
      }
      keepAlive();
    });
  } finally {
    database.close();
  }
}
