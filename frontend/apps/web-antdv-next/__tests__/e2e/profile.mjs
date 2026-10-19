import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';

// eslint-disable-next-line n/no-extraneous-import -- workspace 根目录提供浏览器测试依赖。
import { chromium } from 'playwright';

const base = process.env.T16_BASE_URL || 'http://127.0.0.1:6206';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext();
const page = await context.newPage();
page.on('pageerror', (error) => console.log('PAGE_ERROR', error.message));
const calls = [];
const avatarUrl = 'https://example.test/avatar.png';
let profile = { realName: '原姓名', introduction: '原简介', avatar: null };
const reply = (data) => ({ code: 0, message: 'OK', data });

await page.addInitScript(() => {
  localStorage.setItem(
    'maple-hush-admin:auth:v1',
    JSON.stringify({ epoch: 't16-browser', revision: 0, token: 't16-token' }),
  );
});
await page.route('**/api/**', async (route) => {
  const request = route.request();
  const path = new URL(request.url()).pathname;
  calls.push({ path, method: request.method(), body: request.postData() });
  let data;
  if (path === '/api/user/info') {
    data = {
      userId: '1',
      username: 'admin',
      ...profile,
      roles: ['ordinary_user'],
      homePath: '/dashboard/workspace',
    };
  } else if (path === '/api/auth/codes' || path === '/api/menu/all') {
    data = [];
  } else if (path === '/api/user/profile' && request.method() === 'PUT') {
    const payload = JSON.parse(request.postData());
    profile = { ...profile, ...payload };
    data = { userId: '1', ...payload };
  } else if (path === '/api/user/avatar' && request.method() === 'POST') {
    profile.avatar = avatarUrl;
    data = { avatar: avatarUrl };
  } else if (path === '/api/user/password' && request.method() === 'PUT') {
    data = null;
  } else {
    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: '{}',
    });
    return;
  }
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(reply(data)),
  });
});

try {
  await page.goto(`${base}/#/profile`);
  await page.getByText('基本资料', { exact: true }).waitFor();
  await page.getByLabel('姓名').waitFor();
  assert.equal(await page.getByLabel('登录账号').inputValue(), 'admin');
  assert.equal(await page.getByLabel('角色').inputValue(), 'ordinary_user');
  assert.equal(await page.getByLabel('登录账号').isDisabled(), true);
  assert.equal(await page.getByLabel('角色').isDisabled(), true);
  await page.getByLabel('姓名').fill('更新姓名');
  await page.getByLabel('个人简介').fill('更新简介');
  await Promise.all([
    page.waitForResponse(
      (response) => new URL(response.url()).pathname === '/api/user/profile',
    ),
    page.getByRole('button', { name: '更新基本信息' }).click(),
  ]);
  const saved = calls.findLast((call) => call.path === '/api/user/profile');
  assert.deepEqual(JSON.parse(saved.body), {
    realName: '更新姓名',
    introduction: '更新简介',
  });
  console.log('PASS profile editing and readonly identity fields');

  await Promise.all([
    page.waitForResponse(
      (response) => new URL(response.url()).pathname === '/api/user/avatar',
    ),
    page.locator('input[type="file"]').setInputFiles({
      name: 'avatar.png',
      mimeType: 'image/png',
      buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/S8kAAAAASUVORK5CYII=',
        'base64',
      ),
    }),
  ]);
  assert(
    calls.some(
      (call) => call.path === '/api/user/avatar' && call.method === 'POST',
    ),
  );
  await page
    .locator(`img[src="${avatarUrl}"]`)
    .first()
    .waitFor({ state: 'attached' });
  console.log('PASS avatar endpoint');

  await page.getByText('修改密码', { exact: true }).click();
  await page.getByLabel('旧密码').fill('old-pass-123');
  await page
    .locator('input[autocomplete="new-password"]')
    .nth(0)
    .fill('new-pass-456');
  await page
    .locator('input[autocomplete="new-password"]')
    .nth(1)
    .fill('new-pass-456');
  await page.getByRole('button', { name: '更新密码' }).click();
  await page.waitForURL('**/auth/login');
  const changed = calls.findLast((call) => call.path === '/api/user/password');
  assert.deepEqual(JSON.parse(changed.body), {
    oldPassword: 'old-pass-123',
    newPassword: 'new-pass-456',
  });
  assert.equal(
    JSON.parse(
      await page.evaluate(() =>
        localStorage.getItem('maple-hush-admin:auth:v1'),
      ),
    ).token,
    null,
  );
  assert(!calls.some((call) => call.path === '/api/auth/logout'));
  console.log('PASS password change clears session and returns login');
} finally {
  await browser.close();
}
