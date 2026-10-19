import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { execFileSync } from 'node:child_process';
import { createHmac } from 'node:crypto';
import process from 'node:process';

// eslint-disable-next-line n/no-extraneous-import -- workspace 根目录提供浏览器测试依赖。
import { chromium } from 'playwright';

const base = process.env.T17_BASE_URL || 'http://127.0.0.1:6001';
const schema = process.env.T17_DATABASE;
assert(
  schema && /^maple_hush_t17_[a-z0-9_]+$/.test(schema),
  '必须指定 T17 隔离库',
);
const prefix = process.env.T17_REDIS_PREFIX || schema;
assert(/^maple_hush_t17_[a-z0-9_]+$/.test(prefix), '必须指定 T17 Redis 前缀');
const oldPassword = process.env.T17_PASSWORD || 'T17Browser!Example42';
const newPassword = 'T17Profile!Example43';
const secret =
  process.env.T17_JWT_SECRET || 't17-only-synthetic-jwt-key-at-least-32-bytes';
const sessionKey = 'maple-hush-admin:auth:v1';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ timezoneId: 'Asia/Shanghai' });
const page = await context.newPage();
const pageErrors = [];
page.on('pageerror', (error) => pageErrors.push(error.message));
let captcha;
page.on('response', async (response) => {
  if (
    new URL(response.url()).pathname === '/api/auth/captcha' &&
    response.ok()
  ) {
    captcha = (await response.json()).data;
  }
});

async function login(password) {
  captcha = undefined;
  await page.goto(`${base}/auth/login`);
  try {
    await page.locator('img[alt="图形验证码"]').waitFor();
  } catch (error) {
    console.error(
      'Login view did not render',
      page.url(),
      await page.locator('body').innerText(),
    );
    throw error;
  }
  await page.waitForFunction(
    () => !!document.querySelector('img[alt="图形验证码"]'),
  );
  for (let attempt = 0; attempt < 50 && !captcha; attempt++) {
    await page.waitForTimeout(100);
  }
  assert(captcha, '未收到验证码响应');
  const answer = execFileSync(
    'redis-cli',
    ['-a', '123456', 'GET', `${prefix}:captcha:${captcha.captchaId}`],
    { encoding: 'utf8' },
  ).trim();
  assert(answer, '隔离 Redis 中未找到验证码答案');
  await page.getByPlaceholder('请输入用户名').fill('admin');
  await page.getByPlaceholder('请输入密码', { exact: true }).fill(password);
  await page.getByRole('textbox', { name: '验证码', exact: true }).fill(answer);
  await page.getByRole('button', { name: 'login', exact: true }).click();
  await page.waitForURL('**/dashboard/workspace');
}

async function expireAccess() {
  const state = await page.evaluate(
    (key) => JSON.parse(localStorage.getItem(key)),
    sessionKey,
  );
  const [header, payload] = state.token.split('.');
  const claims = JSON.parse(Buffer.from(payload, 'base64url').toString());
  const expired = `${header}.${Buffer.from(JSON.stringify({ ...claims, exp: 1 })).toString('base64url')}`;
  const token = `${expired}.${createHmac('sha256', secret).update(expired).digest('base64url')}`;
  await page.evaluate(
    ({ key, token }) => {
      const current = JSON.parse(localStorage.getItem(key));
      current.token = token;
      current.revision++;
      localStorage.setItem(key, JSON.stringify(current));
      window.dispatchEvent(new StorageEvent('storage', { key }));
    },
    { key: sessionKey, token },
  );
}

async function concurrentRequests(tab, count) {
  return tab.evaluate(async (quantity) => {
    const { requestClient } = await import('/src/api/request.ts');
    return Promise.all(
      Array.from({ length: quantity }, () =>
        requestClient.get('/user/info').then((user) => user.username),
      ),
    );
  }, count);
}

try {
  await login(oldPassword);
  const secondTab = await context.newPage();
  await secondTab.goto(`${base}/dashboard/workspace`);
  await secondTab.getByText('欢迎').first().waitFor();
  let refreshCount = 0;
  context.on('request', (request) => {
    if (
      new URL(request.url()).pathname === '/api/auth/refresh' &&
      request.method() === 'POST'
    ) {
      refreshCount++;
    }
  });
  await expireAccess();
  assert.deepEqual(await concurrentRequests(page, 6), Array(6).fill('admin'));
  assert.equal(refreshCount, 1);
  await expireAccess();
  assert.deepEqual(
    await Promise.all([
      concurrentRequests(page, 4),
      concurrentRequests(secondTab, 4),
    ]),
    [Array(4).fill('admin'), Array(4).fill('admin')],
  );
  assert.equal(refreshCount, 2);
  assert.equal(
    await page.evaluate((key) => localStorage.getItem(key), sessionKey),
    await secondTab.evaluate((key) => localStorage.getItem(key), sessionKey),
  );
  console.log('PASS same-page and two-tab concurrent refresh');

  const accessBeforeLogout = JSON.parse(
    await page.evaluate((key) => localStorage.getItem(key), sessionKey),
  ).token;
  await expireAccess();
  await page.evaluate(async () => {
    const { useAuthStore } = await import('/src/store/auth.ts');
    await useAuthStore().logout();
  });
  await page.waitForURL('**/auth/login');
  await secondTab.waitForURL('**/auth/login');
  const afterLogout = await context.request.get(`${base}/api/user/info`, {
    headers: { Authorization: `Bearer ${accessBeforeLogout}` },
  });
  assert.equal(afterLogout.status(), 401);
  await secondTab.close();
  console.log('PASS expired-access logout and cross-tab invalidation');

  await login(oldPassword);
  await page.goto(`${base}/profile`);
  await page.getByLabel('姓名').waitFor();
  assert(await page.getByLabel('登录账号').isDisabled());
  assert(await page.getByLabel('角色').isDisabled());
  await page.getByLabel('姓名').fill('T17 回归管理员');
  await page.getByLabel('个人简介').fill('T17 真实资料接口回归');
  await Promise.all([
    page.waitForResponse(
      (response) => new URL(response.url()).pathname === '/api/user/profile',
    ),
    page.getByRole('button', { name: '更新基本信息' }).click(),
  ]);
  await page.reload();
  assert.equal(await page.getByLabel('姓名').inputValue(), 'T17 回归管理员');
  assert.equal(
    await page.getByLabel('个人简介').inputValue(),
    'T17 真实资料接口回归',
  );
  const invalidUploads = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/user/avatar')
      invalidUploads.push(request);
  });
  await page.locator('input[type="file"]').setInputFiles({
    name: 'invalid.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('not an image'),
  });
  await page.getByText('仅支持不超过 2 MB 的 JPG、PNG、WebP 图片').waitFor();
  assert.equal(invalidUploads.length, 0);
  console.log(
    'PASS real profile persistence, readonly identity and invalid avatar rejection',
  );

  const accessBeforePassword = JSON.parse(
    await page.evaluate((key) => localStorage.getItem(key), sessionKey),
  ).token;
  await page.getByText('修改密码', { exact: true }).click();
  await page.getByLabel('旧密码').fill(oldPassword);
  await page
    .locator('input[autocomplete="new-password"]')
    .nth(0)
    .fill(newPassword);
  await page
    .locator('input[autocomplete="new-password"]')
    .nth(1)
    .fill(newPassword);
  await page.getByRole('button', { name: '更新密码' }).click();
  await page.waitForURL('**/auth/login');
  assert.equal(
    JSON.parse(
      await page.evaluate((key) => localStorage.getItem(key), sessionKey),
    ).token,
    null,
  );
  const oldAccess = await context.request.get(`${base}/api/user/info`, {
    headers: { Authorization: `Bearer ${accessBeforePassword}` },
  });
  assert.equal(oldAccess.status(), 401);
  await login(newPassword);
  console.log(
    'PASS self password change revokes old session and new password logs in',
  );
  assert.deepEqual(pageErrors, []);
} finally {
  await browser.close();
}
