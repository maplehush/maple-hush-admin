import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { execFileSync } from 'node:child_process';
import { createHmac } from 'node:crypto';

// eslint-disable-next-line n/no-extraneous-import -- 使用 workspace 根目录的测试依赖。
import { chromium } from 'playwright';
const base = process.env.T14_BASE_URL || 'http://127.0.0.1:6001';
const prefix = process.env.T14_REDIS_PREFIX || 'maple_hush_t14_browser';
const database = process.env.T14_DATABASE || 'maple_hush_t14_browser';
assert(/^maple_hush_t14_[a-z0-9_]+$/.test(database), '仅允许 T14 隔离测试库');
assert(
  /^maple_hush_t14_[a-z0-9_]+$/.test(prefix),
  '仅允许 T14 隔离 Redis 前缀',
);
const secret =
  process.env.T14_JWT_SECRET ||
  't14-browser-only-synthetic-secret-at-least-32-bytes';
const password = process.env.T14_PASSWORD || 'T14Browser!Example42';
const key = 'maple-hush-admin:auth:v1';
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
const context = await browser.newContext({ timezoneId: 'Asia/Shanghai' });
const page = await context.newPage();
let csrfResponses = 0;
page.on('response', (r) => {
  if (new URL(r.url()).pathname === '/api/auth/csrf' && r.ok()) {
    csrfResponses++;
  }
});
page.on('pageerror', (error) => console.log('PAGE_ERROR', error.message));
page.on('console', (msg) => {
  if (msg.type() === 'error')
    console.log('CONSOLE_ERROR', msg.text().slice(0, 200));
});
let captcha;
page.on('response', async (response) => {
  if (response.url().endsWith('/api/auth/captcha') && response.ok()) {
    captcha = (await response.json()).data;
  }
});
async function waitCaptcha() {
  await page.waitForFunction(
    () => !!document.querySelector('img[alt="图形验证码"]'),
  );
  for (let i = 0; i < 50 && !captcha; i++) {
    await page.waitForTimeout(100);
  }
  assert(captcha);
}
function answer() {
  return execFileSync(
    'redis-cli',
    ['-a', '123456', 'GET', `${prefix}:captcha:${captcha.captchaId}`],
    { encoding: 'utf8' },
  ).trim();
}
async function login(pass = password, username = 'admin') {
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码', { exact: true }).fill(pass);
  await page
    .getByRole('textbox', { name: '验证码', exact: true })
    .fill(answer());
  await page.getByRole('button', { name: 'login', exact: true }).click();
}
try {
  await page.goto(`${base}/auth/login`);
  await waitCaptcha();
  console.log(
    'INPUTS',
    await page.locator('input').evaluateAll((inputs) =>
      inputs.map((i) => ({
        placeholder: i.placeholder,
        type: i.type,
        value: i.value,
      })),
    ),
  );
  const firstId = captcha.captchaId;
  await page.getByRole('button', { name: '刷新验证码' }).click();
  await page.waitForTimeout(500);
  assert.notEqual(captcha.captchaId, firstId);
  console.log('PASS captcha refresh');
  await page.getByPlaceholder('请输入用户名').fill('admin');
  await page.getByPlaceholder('请输入密码', { exact: true }).fill('12345678');
  await page.getByRole('textbox', { name: '验证码', exact: true }).fill('WRNG');
  const failedId = captcha.captchaId;
  await page.getByRole('button', { name: 'login', exact: true }).click();
  await page.waitForTimeout(800);
  assert.notEqual(captcha.captchaId, failedId);
  console.log('PASS failed captcha renewed');
  if (!process.env.T14_SKIP_INITIAL) {
    await login(process.env.T14_INITIAL_PASSWORD || '12345678');
    await page.waitForURL('**/auth/initial-password');
    assert.equal(
      await page.evaluate(
        (k) => JSON.parse(localStorage.getItem(k)).token,
        key,
      ),
      null,
    );
    const storage = await page.evaluate(() =>
      JSON.stringify({ ...localStorage, ...sessionStorage }),
    );
    assert(!storage.includes('passwordChangeToken'));
    console.log('PASS pending credential not persisted');
    await page
      .getByRole('textbox', { name: '新密码', exact: true })
      .fill(password);
    await page
      .getByRole('textbox', { name: '确认新密码', exact: true })
      .fill(password);
    captcha = undefined;
    await page.getByRole('button', { name: '确认修改' }).click();
    await page.waitForURL('**/auth/login');
    await waitCaptcha();
    console.log('PASS initial password returns login');
  }
  const csrfBeforeNormalLogin = csrfResponses;
  await login();
  await page.waitForURL('**/dashboard/workspace');
  await page.waitForTimeout(1500);
  console.log('HOME', (await page.locator('body').innerText()).slice(0, 1800));
  await page.screenshot({
    path: process.env.TEMP + '/maple-t14-home.png',
    fullPage: true,
  });
  console.log('PASS normal login');
  assert(csrfResponses > csrfBeforeNormalLogin);
  console.log('PASS application acquires CSRF after login');
  for (const segment of [
    'user',
    'role',
    'menu',
    'dept',
    'dict',
    'login-log',
    'operation-log',
  ]) {
    await page.goto(`${base}/system/${segment}`);
    await page
      .getByText('功能尚未开放，真实查询与操作将在 T15 接入。')
      .waitFor();
  }
  console.log('PASS seven backend menu components');
  const another = await context.newPage();
  await another.goto(`${base}/dashboard/workspace`);
  await another.getByText('欢迎，超级管理员').waitFor();
  let refreshCount = 0;
  context.on('request', (request) => {
    if (
      new URL(request.url()).pathname === '/api/auth/refresh' &&
      request.method() === 'POST'
    )
      refreshCount++;
  });
  async function expire() {
    const state = await page.evaluate(
      (k) => JSON.parse(localStorage.getItem(k)),
      key,
    );
    const [header, payload] = state.token.split('.');
    const claims = JSON.parse(Buffer.from(payload, 'base64url').toString());
    const expired = `${header}.${Buffer.from(JSON.stringify({ ...claims, exp: 1 })).toString('base64url')}`;
    const token = `${expired}.${createHmac('sha256', secret).update(expired).digest('base64url')}`;
    await page.evaluate(
      ({ key, token }) => {
        const state = JSON.parse(localStorage.getItem(key));
        state.token = token;
        state.revision++;
        localStorage.setItem(key, JSON.stringify(state));
        window.dispatchEvent(new StorageEvent('storage', { key }));
      },
      { key, token },
    );
    return token;
  }
  async function requests(tab, count) {
    return tab.evaluate(async (count) => {
      const { requestClient } = await import('/src/api/request.ts');
      return Promise.all(
        Array.from({ length: count }, () =>
          requestClient.get('/user/info').then((data) => data.username),
        ),
      );
    }, count);
  }
  await expire();
  assert.deepEqual(await requests(page, 6), Array(6).fill('admin'));
  assert.equal(refreshCount, 1);
  console.log('PASS same-page six concurrent 401: one refresh');
  await expire();
  const both = await Promise.all([requests(page, 4), requests(another, 4)]);
  assert.deepEqual(both, [Array(4).fill('admin'), Array(4).fill('admin')]);
  assert.equal(refreshCount, 2);
  const state1 = await page.evaluate((k) => localStorage.getItem(k), key);
  const state2 = await another.evaluate((k) => localStorage.getItem(k), key);
  assert.equal(state1, state2);
  console.log('PASS two tabs eight concurrent 401: one refresh, same token');
  const noCsrf = await page.evaluate(
    async () => (await fetch('/api/auth/refresh', { method: 'POST' })).status,
  );
  assert.equal(noCsrf, 403);
  assert.equal(refreshCount, 3);
  const csrf = await context.request.get(`${base}/api/auth/csrf`);
  const csrfBefore = (await csrf.json()).data.token;
  const badCsrf = await page.evaluate(
    async () =>
      (
        await fetch('/api/auth/refresh', {
          method: 'POST',
          headers: { 'X-XSRF-TOKEN': 'invalid' },
        })
      ).status,
  );
  assert.equal(badCsrf, 403);
  assert.equal(refreshCount, 4);
  console.log('PASS missing/invalid CSRF rejected without refresh recursion');
  const validAccess = JSON.parse(state1).token;
  await expire();
  await page.evaluate(async () => {
    const { useAuthStore } = await import('/src/store/auth.ts');
    await useAuthStore().logout();
  });
  await page.waitForURL('**/auth/login');
  await another.waitForURL('**/auth/login');
  assert(csrfResponses > csrfBeforeNormalLogin);
  console.log('PASS application reacquires CSRF after logout');
  const oldAccess = await context.request.get(`${base}/api/user/info`, {
    headers: { Authorization: `Bearer ${validAccess}` },
  });
  assert.equal(oldAccess.status(), 401);
  const csrfAfter = (
    await (await context.request.get(`${base}/api/auth/csrf`)).json()
  ).data.token;
  assert.notEqual(csrfBefore, csrfAfter);
  console.log(
    'PASS expired-access logout, cross-tab logout, old access rejected, CSRF renewed',
  );
  await another.close();
  // Fixture only: clone the isolated admin password hash and grant a normal role one page.
  execFileSync(
    'mysql',
    [
      '--host=127.0.0.1',
      '--user=root',
      process.env.T14_DATABASE || 'maple_hush_t14_browser',
      '-e',
      "INSERT INTO sys_role(role_code,role_name) VALUES('t14_reader','Reader') ON DUPLICATE KEY UPDATE role_name='Reader'; INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT id,10 FROM sys_role WHERE role_code='t14_reader'; INSERT INTO sys_user(username,real_name,password,must_change_password) SELECT 't14_reader','Reader',password,0 FROM sys_user WHERE username='admin' ON DUPLICATE KEY UPDATE password=VALUES(password),must_change_password=0; INSERT IGNORE INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.role_code='t14_reader' WHERE u.username='t14_reader';",
    ],
    { env: { ...process.env, MYSQL_PWD: '123456' } },
  );
  await waitCaptcha();
  await login(password, 't14_reader');
  await page.waitForURL('**/dashboard/workspace');
  await page.goto(`${base}/system/user`);
  await page.getByText('功能尚未开放，真实查询与操作将在 T15 接入。').waitFor();
  await page.goto(`${base}/system/role`);
  await page.waitForURL('**/forbidden');
  await page.waitForTimeout(1500);
  const denied = await page.evaluate(async () => {
    const { requestClient } = await import('/src/api/request.ts');
    try {
      await requestClient.get('/system/role/list');
      return 200;
    } catch (e) {
      return e.response.status;
    }
  });
  assert.equal(denied, 403);
  console.log(
    'PASS normal user allowed page, unauthorized route and API denied',
  );
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({
      status: 403,
      contentType: 'application/json',
      body: JSON.stringify({ code: 40301, message: 'CSRF 凭据失效' }),
    }),
  );
  await expire();
  const failed = await page.evaluate(async () => {
    const { requestClient } = await import('/src/api/request.ts');
    return Promise.allSettled(
      Array.from({ length: 5 }, () => requestClient.get('/user/info')),
    ).then((items) => items.every((i) => i.status === 'rejected'));
  });
  assert(failed);
  await page.waitForURL('**/auth/login');
  console.log('PASS refresh 403 settles all five waiters and requires login');
  await page.unroute('**/api/auth/refresh');
  await waitCaptcha();
  await page.evaluate((key) => {
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = function (name, value) {
      if (name === key) {
        throw new DOMException('test quota', 'QuotaExceededError');
      }
      return original.call(this, name, value);
    };
    window.addEventListener(
      't14-restore-storage',
      () => {
        Storage.prototype.setItem = original;
      },
      { once: true },
    );
  }, key);
  await login(password, 't14_reader');
  await page.getByText('无法保存会话，请重新登录').first().waitFor();
  assert.equal(
    await page.evaluate((key) => localStorage.getItem(key), key),
    null,
  );
  assert(page.url().endsWith('/auth/login'));
  await page.evaluate(() =>
    window.dispatchEvent(new Event('t14-restore-storage')),
  );
  console.log('PASS shared session storage failure requires login');
  for (const timezoneId of ['Asia/Shanghai', 'America/New_York']) {
    const dates = await browser.newContext({ timezoneId });
    const tab = await dates.newPage();
    await tab.goto(`${base}/__tests__/e2e/time-check.html`);
    await tab.locator('input').waitFor();
    assert.equal(
      await tab.locator('input').inputValue(),
      '2026-10-14 10:20:30',
    );
    await tab.locator('input').click();
    console.log(`PASS date picker wall clock ${timezoneId}`);
    await dates.close();
  }
} catch (e) {
  console.log('BODY', await page.locator('body').innerText());
  console.log('URL', page.url());
  throw e;
} finally {
  await browser.close();
}
