import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { after, before, test } from 'node:test';

import { chromium } from 'playwright';

// Build the target application first; only API responses are simulated here.
const dist = new URL('../../apps/web-antdv-next/dist/', import.meta.url);
let browser;
let server;
let port;
const types = { '.css': 'text/css', '.js': 'text/javascript', '.json': 'application/json', '.svg': 'image/svg+xml' };
before(async () => {
  server = createServer(async (req, res) => {
    try {
      const path = new URL(req.url, 'http://test').pathname;
      if (path.startsWith('/api/')) {
        let body = '';
        for await (const chunk of req) body += chunk;
        let data = null;
        switch (path) {
          case '/api/auth/captcha':
            data = { captchaId: 'fixture-captcha', imageBase64: 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7' };
            break;
          case '/api/auth/csrf': data = { token: 'fixture-csrf' }; break;
          case '/api/auth/login':
            data = JSON.parse(body).username === 'initial'
              ? { passwordChangeRequired: true, passwordChangeToken: 'fixture-initial' }
              : { passwordChangeRequired: false, accessToken: 'fixture-access' };
            break;
          case '/api/user/info':
            data = { userId: '1', username: 'admin', realName: 'Test User', avatar: null, introduction: '', roles: ['super_admin'], homePath: '/dashboard/workspace' };
            break;
          case '/api/auth/codes': data = []; break;
          case '/api/menu/all':
            data = [{ name: 'Dashboard', path: '/dashboard', component: 'BasicLayout', meta: { title: '概览' }, children: [{ name: 'Workspace', path: '/dashboard/workspace', component: '/dashboard/workspace/index', meta: { title: '工作台' } }] }];
            break;
          case '/api/auth/initial-password':
          case '/api/auth/logout': break;
          default: res.writeHead(404).end(); return;
        }
        res.setHeader('Content-Type', 'application/json');
        return res.end(JSON.stringify({ code: 0, data, error: null, message: 'ok' }));
      }
      const file = new URL(path === '/' ? 'index.html' : `.${path}`, dist);
      if (!file.href.startsWith(dist.href)) { res.writeHead(403).end(); return; }
      const extension = /\.[^.]+$/.exec(path)?.[0];
      res.setHeader('Content-Type', types[extension] || 'text/html');
      res.end(await readFile(file));
    } catch {
      res.writeHead(404).end();
    }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  port = server.address().port;
  browser = await chromium.launch({
    executablePath: process.env.BROWSER_EXECUTABLE || (process.platform === 'win32' ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe' : undefined),
    args: ['--no-proxy-server', '--host-resolver-rules=MAP maple-login.test 127.0.0.1'],
  });
});
after(async () => {
  await browser?.close();
  server?.closeAllConnections();
  await new Promise(resolve => server?.close(resolve));
});

for (const initial of [false, true]) {
  test(`production UI on HTTP: ${initial ? 'initial password change returns to login' : 'successful login reaches workspace'}`, async () => {
    const context = await browser.newContext();
    try {
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(`http://maple-login.test:${port}/#/auth/login`);
      assert.equal(await page.evaluate(() => isSecureContext), false);
      await page.getByPlaceholder(/请输入用户名|Enter username/).fill(initial ? 'initial' : 'admin');
      await page.getByPlaceholder(/请输入密码|Enter password/, { exact: true }).fill('FixturePass42!');
      await page.getByPlaceholder(/请输入验证码|Enter verification code/).fill('ABCD');
      await page.getByRole('button', { name: /^登\s*录$|^Sign in$|^Login$/i }).click();
      await page.waitForURL(initial ? /\/auth\/initial-password/ : /\/dashboard\/workspace/);
      if (initial) {
        await page.getByPlaceholder(/请输入新密码|Enter new password/, { exact: true }).fill('FixtureNew42!');
        await page.getByPlaceholder(/请再次输入新密码|Re-enter new password/).fill('FixtureNew42!');
        await page.getByRole('button', { name: /确认修改|Confirm Change/ }).click();
        await page.waitForURL(/\/auth\/login/);
      }
      assert.deepEqual(errors, []);
    } finally {
      await context.close();
    }
  });
}

test('production UI reports blocked browser storage separately from credential errors', async () => {
  const context = await browser.newContext();
  try {
    await context.addInitScript(() => {
      Object.defineProperty(window, 'indexedDB', {
        value: { open() { throw new DOMException('blocked', 'SecurityError'); } },
      });
    });
    const page = await context.newPage();
    await page.goto(`http://maple-login.test:${port}/#/auth/login`);
    await page.getByPlaceholder(/请输入用户名|Enter username/).fill('admin');
    await page.getByPlaceholder(/请输入密码|Enter password/, { exact: true }).fill('FixturePass42!');
    await page.getByPlaceholder(/请输入验证码|Enter verification code/).fill('ABCD');
    await page.getByRole('button', { name: /^登\s*录$|^Sign in$|^Login$/i }).click();
    await page.getByText(/无法协调浏览器会话|Could not coordinate your browser session/).waitFor();
    assert.equal(await page.getByText('登录失败，请检查账号、密码和验证码').count(), 0);
  } finally {
    await context.close();
  }
});
