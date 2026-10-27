import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { after, before, test } from 'node:test';

import { chromium } from 'playwright';
import ts from 'typescript';

// Run from frontend/: node --test scripts/e2e/http-session.test.mjs
// The server loads the production session modules, with only HTTP transport replaced.
const source = new URL('../../apps/web-antdv-next/src/auth/', import.meta.url);
let browser;
let server;
let port;
let refreshes = 0;
const html = `<!doctype html><script type="module">
import { session } from '/session'; window.session = session; window.ready = true;
import { withSessionLock } from '/browser-session'; window.withSessionLock = withSessionLock;
</script>`;
before(async () => {
  server = createServer(async (req, res) => {
    try {
      if (req.url === '/') {
        res.setHeader('Content-Type', 'text/html');
        return res.end(html);
      }
      if (req.url === '/auth-http') {
        res.setHeader('Content-Type', 'text/javascript');
        return res.end(`export const authHttp = {
          login: async data => {
            if (data.username === 'reject') throw new Error('rejected');
            return data.username === 'initial'
              ? {passwordChangeRequired:true,passwordChangeToken:'initial-only'}
              : {passwordChangeRequired:false,accessToken:'old-access'};
          },
          refresh: async () => {
            const response = await fetch('/refresh');
            return response.json();
          },
          logout: async () => {}
        };`);
      }
      if (req.url === '/refresh') {
        refreshes += 1;
        const token = `renewed-${refreshes}`;
        await new Promise(resolve => setTimeout(resolve, 100));
        res.setHeader('Content-Type', 'application/json');
        return res.end(JSON.stringify(token));
      }
      if (/^\/[a-z-]+$/.test(req.url || '')) {
        const code = await readFile(new URL(`${req.url.slice(1)}.ts`, source), 'utf8');
        res.setHeader('Content-Type', 'text/javascript');
        return res.end(ts.transpileModule(code, {
          compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
        }).outputText);
      }
      res.writeHead(404).end();
    } catch {
      res.writeHead(500).end('Test module could not be loaded');
    }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  port = server.address().port;
  browser = await chromium.launch({
    executablePath: process.env.BROWSER_EXECUTABLE || (process.platform === 'win32' ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe' : undefined),
    args: ['--no-proxy-server', '--host-resolver-rules=MAP maple-session.test 127.0.0.1'],
  });
});
after(async () => {
  await browser?.close();
  server?.closeAllConnections();
  await new Promise(resolve => server?.close(resolve));
});

for (const secure of [false, true]) {
  test(`${secure ? 'secure localhost' : 'insecure HTTP'}: login, two-tab refresh, logout and initial-password isolation`, async () => {
    const context = await browser.newContext();
    try {
      const url = `http://${secure ? '127.0.0.1' : 'maple-session.test'}:${port}`;
      const a = await context.newPage();
      const b = await context.newPage();
      await Promise.all([a.goto(url), b.goto(url)]);
      await Promise.all([a.waitForFunction(() => window.ready), b.waitForFunction(() => window.ready)]);
      assert.equal(await a.evaluate(() => isSecureContext), secure);
      if (!secure) {
        assert.equal(await a.evaluate(() => typeof crypto.randomUUID), 'undefined');
        assert.equal(await a.evaluate(() => typeof navigator.locks), 'undefined');
      }
      await a.evaluate(() => session.login({ username: 'normal' }));
      assert.equal(await b.evaluate(() => session.state.token), 'old-access');
      const count = refreshes;
      const results = await Promise.all([a, b].map(page => page.evaluate(async () => {
        const { epoch, token } = session.state;
        return Promise.all(Array.from({ length: 8 }, () => session.refresh(token, epoch)));
      })));
      assert.equal(refreshes - count, 1, 'both tabs must share one rotation');
      assert.equal(new Set(results.flat()).size, 1);
      await b.evaluate(() => session.logout());
      assert.equal(await a.evaluate(() => session.state.token), null);
      await assert.rejects(a.evaluate(() => session.login({ username: 'reject' })));
      await a.evaluate(() => session.login({ username: 'initial' }));
      assert.equal(await b.evaluate(() => session.state.token), null);
      assert.equal(await a.evaluate(() => localStorage.getItem('maple-hush-admin:auth:v1').includes('initial-only')), false);
      // Closing the owning page must not leave a time-based lease or permanent lock.
      await a.evaluate(() => {
        void withSessionLock(() => new Promise(() => { window.holdingLock = true; }));
      });
      await a.waitForFunction(() => window.holdingLock);
      await a.close();
      assert.equal(await b.evaluate(() => withSessionLock(async () => 'released')), 'released');
    } finally {
      await context.close();
    }
  });
}
