import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { execFileSync } from 'node:child_process';
import process from 'node:process';

// eslint-disable-next-line n/no-extraneous-import -- workspace 根目录测试依赖。
import { chromium } from 'playwright';
const base = process.env.T15_BASE || 'http://127.0.0.1:6001/#';
const schema = process.env.T15_DATABASE;
assert(
  schema && /^maple_hush_t15_[a-z0-9_]+$/.test(schema),
  '必须使用T15隔离库与同名前缀',
);
const pass = 'T15Browser!Example42';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({
  timezoneId: 'Asia/Shanghai',
  viewport: { width: 1440, height: 1000 },
});
const page = await context.newPage();
page.setDefaultNavigationTimeout(120000);
page.on('requestfailed', (r) =>
  console.log('REQUEST_FAILED', r.url(), r.failure()?.errorText),
);
const errors = [];
page.on('pageerror', (e) => {
  errors.push(e.message);
  console.log('PAGE_ERROR', e.message);
});
let captcha;
page.on('response', async (r) => {
  if (r.url().endsWith('/api/auth/captcha') && r.ok()) {
    try {
      captcha = (await r.json()).data;
    } catch {
      // 退出后的自动跳转可能取消旧页面验证码响应；下一次 login 会读取新响应。
    }
  }
});
async function login(password = pass, username = 'admin') {
  captcha = undefined;
  await page.goto(base + '/auth/login');
  await page.locator('img[alt="图形验证码"]').waitFor();
  for (let i = 0; i < 50 && !captcha; i++) {
    await page.waitForTimeout(100);
  }
  const code = execFileSync(
    'redis-cli',
    ['-a', '123456', 'GET', `${schema}:captcha:${captcha.captchaId}`],
    { encoding: 'utf8' },
  ).trim();
  await page.getByPlaceholder('请输入用户名').fill(username);
  await page.getByPlaceholder('请输入密码', { exact: true }).fill(password);
  await page.getByRole('textbox', { name: '验证码', exact: true }).fill(code);
  await page.getByRole('button', { name: 'login', exact: true }).click();
}
try {
  await login(process.env.T15_SKIP_INITIAL ? pass : '12345678');
  if (!process.env.T15_SKIP_INITIAL) {
    await page.waitForURL('**/auth/initial-password');
    await page.getByRole('textbox', { name: '新密码', exact: true }).fill(pass);
    await page
      .getByRole('textbox', { name: '确认新密码', exact: true })
      .fill(pass);
    await page.getByRole('button', { name: '确认修改' }).click();
    await page.waitForURL('**/auth/login');
    captcha = undefined;
    await login();
  }
  await page.waitForURL('**/dashboard/workspace');
  for (const name of [
    'user',
    'role',
    'menu',
    'dept',
    'dict',
    'login-log',
    'operation-log',
  ]) {
    await page.goto(`${base}/system/${name}`);
    await page.locator('.vxe-table').first().waitFor();
    await page.waitForTimeout(900);
    console.log(
      'PAGE',
      name,
      (await page.locator('body').innerText()).slice(-1800),
    );
    await page.screenshot({
      path: `${process.env.TEMP}/maple-t15-${name}.png`,
      fullPage: true,
    });
  }
  async function api(path, method = 'get', data) {
    return page.evaluate(
      async ({ path, method, data }) => {
        const token = JSON.parse(
          localStorage.getItem('maple-hush-admin:auth:v1') || 'null',
        )?.token;
        const response = await fetch(`/api${path}`, {
          method: method.toUpperCase(),
          credentials: 'include',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          body: data === undefined ? undefined : JSON.stringify(data),
        });
        const body = await response.json();
        if (!response.ok || body.code !== 0)
          throw new Error(`${response.status}: ${body.message}`);
        return body.data;
      },
      { path, method, data },
    );
  }
  async function saveDialog(path, method = 'POST') {
    const dialog = page.getByRole('dialog').last();
    const response = page.waitForResponse(
      (r) =>
        new URL(r.url()).pathname === `/api${path}` &&
        r.request().method() === method,
    );
    await dialog
      .getByRole('button', { name: /确\s*定|确\s*认|保\s*存/ })
      .last()
      .click();
    assert.equal((await response).status(), 200);
    await dialog.waitFor({ state: 'hidden' });
  }
  const recordRow = (id) => page.locator('.vxe-body--row[rowid="' + id + '"]');
  const stamp = Date.now().toString().slice(-7);
  await page.goto(`${base}/system/dict`);
  await page.getByRole('button', { name: '新增类型', exact: true }).click();
  await page
    .getByRole('dialog')
    .getByLabel('字典编码')
    .fill(`t15_status_${stamp}`);
  await page
    .getByRole('dialog')
    .getByLabel('类型名称')
    .fill(`验收字典${stamp}`);
  await saveDialog('/system/dict/type');
  const types = await api('/system/dict/type/list');
  const type = types.items.find((t) => t.dictCode === `t15_status_${stamp}`);
  assert(type);
  await page
    .locator('.vxe-body--row')
    .filter({ hasText: `t15_status_${stamp}` })
    .getByRole('button', { name: '管理字典项', exact: true })
    .last()
    .click();
  await page.getByRole('button', { name: '新增字典项', exact: true }).click();
  await page.getByRole('dialog').getByLabel('标签').fill('启用');
  await page.getByRole('dialog').getByLabel('字典值').fill('1');
  await page
    .getByRole('dialog')
    .getByPlaceholder('#RRGGBB，留空使用普通样式')
    .fill('#22c55e');
  await saveDialog('/system/dict/item');
  let items = await api(`/system/dict/item/list?dictTypeId=${type.id}`);
  assert.equal(items.items[0].color, '#22c55e');
  assert.equal(
    (await api(`/system/dict/type/${type.dictCode}/items`))[0].label,
    '启用',
  );
  console.log(
    'PASS dictionary Vben type/item creation and enabled option color',
  );
  // 字典编辑保留真实类型、颜色与默认项；随后清除颜色并停用。
  await page
    .getByRole('button', { name: '编辑字典项', exact: true })
    .first()
    .click();
  await page
    .getByRole('dialog')
    .getByLabel('标签', { exact: false })
    .fill('启用（已改）');
  await page.getByRole('dialog').getByRole('checkbox').check();
  await saveDialog(`/system/dict/item/${items.items[0].id}`, 'PUT');
  items = await api(`/system/dict/item/list?dictTypeId=${type.id}`);
  assert.equal(items.items[0].defaultItem, true);
  await page
    .getByRole('button', { name: '编辑字典项', exact: true })
    .first()
    .click();
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /清\s*除/ })
    .click();
  await saveDialog(`/system/dict/item/${items.items[0].id}`, 'PUT');
  assert.equal(
    (await api(`/system/dict/item/list?dictTypeId=${type.id}`)).items[0].color,
    null,
  );
  console.log('PASS dictionary edit/default/color clear');
  // 部门官方 VbenModal。
  await page.goto(`${base}/system/dept`);
  await page.getByRole('button', { name: /新增.*部门/ }).click();
  await page
    .getByRole('dialog')
    .getByLabel('部门名称')
    .fill(`验收部门${stamp}`);
  await saveDialog('/system/dept');
  let depts = await api('/system/dept/list');
  let dept = depts.find((d) => d.name === `验收部门${stamp}`);
  assert(dept);
  await page
    .locator('.vxe-body--row')
    .filter({ hasText: dept.name })
    .getByRole('button', { name: /编\s*辑|修\s*改/ })
    .last()
    .click();
  await page
    .getByRole('dialog')
    .getByLabel('部门名称')
    .fill(`已改部门${stamp}`);
  await saveDialog(`/system/dept/${dept.id}`, 'PUT');
  console.log('PASS department Vben create/edit');
  // 角色官方 Grid + Drawer：创建时不授予任何权限，再走独立授权。
  await page.goto(`${base}/system/role`);
  await page.getByRole('button', { name: /新增.*角色/ }).click();
  await page
    .getByRole('dialog')
    .getByLabel('角色编码')
    .fill(`t15_reader_${stamp}`);
  await page
    .getByRole('dialog')
    .getByLabel('角色名称')
    .fill(`验收角色${stamp}`);
  await saveDialog('/system/role');
  let roles = await api('/system/role/list');
  let role = roles.items.find((r) => r.roleCode === `t15_reader_${stamp}`);
  assert(role);
  await recordRow(role.id)
    .getByRole('button', { name: /编\s*辑|修\s*改/ })
    .last()
    .click();
  await page
    .getByRole('dialog')
    .getByLabel('角色名称')
    .fill(`已改角色${stamp}`);
  await saveDialog(`/system/role/${role.id}`, 'PUT');
  console.log('PASS role Vben create/edit');
  role = await api(`/system/role/${role.id}`);
  await api(`/system/role/${role.id}`, 'put', { name: role.name, status: 0 });
  await page.route('**/api/system/menu/list', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 50300,
        message: 'isolated-test-unavailable',
      }),
    }),
  );
  await recordRow(role.id)
    .getByRole('button', { name: '菜单权限', exact: true })
    .last()
    .click();
  await page
    .getByText('角色或菜单加载失败，请关闭后重试', { exact: true })
    .waitFor();
  assert.equal(
    await page
      .getByRole('dialog')
      .getByRole('button', { name: /确\s*定|确\s*认|保\s*存/ })
      .count(),
    0,
  );
  await page.keyboard.press('Escape');
  await page.unroute('**/api/system/menu/list');
  assert.equal((await api(`/system/role/${role.id}`)).status, 0);
  await api(`/system/role/${role.id}`, 'put', { name: role.name, status: 1 });
  console.log(
    'PASS failed role authorization initialization cannot save or enable disabled role',
  );
  // 用户官方 Grid + Drawer，统一上传接口使用显式存储替身，不连接 OSS。
  await page.goto(`${base}/system/user`);
  await page.getByRole('button', { name: /新增.*用户/ }).click();
  await page
    .getByRole('dialog')
    .getByLabel('登录账号')
    .fill(`t15_user_${stamp}`);
  await page.getByRole('dialog').getByLabel('姓名').fill(`验收用户${stamp}`);
  const uploadId = 'a'.repeat(25) + stamp;
  const uploadUrl = `https://maple-hush.oss-cn-beijing.aliyuncs.com/vben-admin-img/files/1/${uploadId}.png`;
  await page.route(uploadUrl, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'image/png',
      body: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=',
        'base64',
      ),
    }),
  );
  execFileSync('redis-cli', [
    '-a',
    '123456',
    'SET',
    `${schema}:file_upload:${uploadId}`,
    JSON.stringify({ userId: 1, url: uploadUrl }),
    'EX',
    '7200',
  ]);
  await page.route('**/api/files/upload', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        data: {
          uploadId,
          name: 'test.png',
          url: uploadUrl,
          contentType: 'image/png',
          size: 68,
        },
      }),
    }),
  );
  await page
    .getByRole('dialog')
    .locator('input[type=file]')
    .setInputFiles({
      name: 'test.png',
      mimeType: 'image/png',
      buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=',
        'base64',
      ),
    });
  await page
    .getByRole('dialog')
    .getByText('上传成功，保存用户后生效。', { exact: true })
    .waitFor();
  await saveDialog('/system/user');
  const users = await api(
    `/system/user/list?name=${encodeURIComponent('验收用户' + stamp)}`,
  );
  const target = users.items[0];
  assert(target);
  assert.equal(target.avatar, uploadUrl);
  await recordRow(target.id)
    .getByRole('button', { name: /编\s*辑|修\s*改/ })
    .last()
    .click();
  await page.getByRole('dialog').getByLabel('姓名').fill(`已改用户${stamp}`);
  const update = page.waitForRequest(
    (r) =>
      new URL(r.url()).pathname === `/api/system/user/${target.id}` &&
      r.method() === 'PUT',
  );
  await saveDialog(`/system/user/${target.id}`, 'PUT');
  assert(!('avatarUploadId' in (await update).postDataJSON()));
  assert.equal((await api(`/system/user/${target.id}`)).avatar, uploadUrl);
  console.log(
    'PASS user Vben create/edit, shared upload binding and unchanged-avatar omission',
  );
  await page.goto(`${base}/system/role`);
  await recordRow(role.id)
    .getByRole('button', { name: '菜单权限', exact: true })
    .last()
    .click();
  await page.getByRole('dialog').getByText('用户管理', { exact: true }).click();
  await saveDialog(`/system/role/${role.id}`, 'PUT');
  role = await api(`/system/role/${role.id}`);
  assert(role.permissions.includes('10'));
  assert(!role.permissions.includes('11'));
  console.log(
    'PASS strict Vben role tree submits page IDs without silently granting descendant buttons',
  );
  await page.goto(`${base}/system/user`);
  assert.equal(
    await recordRow('1')
      .getByRole('button', { name: '分配角色', exact: true })
      .count(),
    0,
  );
  assert.equal(
    await recordRow('1')
      .getByRole('button', { name: '重置密码', exact: true })
      .count(),
    0,
  );
  await recordRow(target.id)
    .getByRole('button', { name: '更多', exact: true })
    .last()
    .click();
  await page.getByRole('menuitem', { name: '分配角色', exact: true }).click();
  await page.getByRole('dialog').getByRole('combobox').click();
  await page
    .locator('.ant-select-item-option-content')
    .filter({ hasText: `已改角色${stamp}` })
    .click();
  await saveDialog(`/system/user/${target.id}/roles`, 'PUT');
  assert.deepEqual((await api(`/system/user/${target.id}`)).roleIds, [role.id]);
  await recordRow(target.id)
    .getByRole('button', { name: '更多', exact: true })
    .last()
    .click();
  await page.getByRole('menuitem', { name: '重置密码', exact: true }).click();
  await saveDialog(`/system/user/${target.id}/reset-password`);
  assert.equal((await api('/user/info')).username, 'admin');
  console.log(
    'PASS protected super-admin buttons, independent user role assignment and reset without logging operator out',
  );
  for (const logKind of ['login-log', 'operation-log']) {
    await page.goto(`${base}/system/${logKind}`);
    await page
      .getByRole('button', { name: '查看详情', exact: true })
      .first()
      .click();
    await page
      .getByRole('dialog')
      .getByText('Trace ID', { exact: true })
      .waitFor();
    await page.keyboard.press('Escape');
  }
  console.log('PASS both audit query tables and Vben detail drawers');
  // 菜单新增/编辑/删除后均真实退出，再用已有新密码登录。
  await page.goto(`${base}/system/menu`);
  await page.getByRole('button', { name: /新增.*菜单/ }).click();
  await page.getByRole('dialog').getByText('目录', { exact: true }).click();
  await page.getByRole('dialog').getByLabel('菜单名称').fill(`T15Menu${stamp}`);
  await page
    .getByRole('dialog')
    .getByLabel('标题', { exact: false })
    .fill(`验收菜单${stamp}`);
  await page.getByRole('dialog').getByLabel('路由地址').fill(`/t15-${stamp}`);
  await saveDialog('/system/menu');
  await page.waitForURL('**/auth/login');
  await login();
  await page.waitForURL('**/dashboard/workspace');
  let tree = await api('/system/menu/list');
  const menu = tree.find((m) => m.name === `T15Menu${stamp}`);
  assert(menu);
  await page.goto(`${base}/system/menu`);
  await recordRow(menu.id)
    .getByRole('button', { name: /修\s*改|编\s*辑/ })
    .last()
    .click();
  await page
    .getByRole('dialog')
    .getByLabel('标题', { exact: false })
    .fill(`已改菜单${stamp}`);
  await saveDialog(`/system/menu/${menu.id}`, 'PUT');
  await page.waitForURL('**/auth/login');
  await login();
  await page.waitForURL('**/dashboard/workspace');
  await page.goto(`${base}/system/menu`);
  await recordRow(menu.id)
    .getByRole('button', { name: /删\s*除/ })
    .last()
    .click();
  const menuDeleted = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === `/api/system/menu/${menu.id}` &&
      r.request().method() === 'DELETE',
  );
  await page
    .getByRole('button', { name: /确\s*定|确\s*认/ })
    .last()
    .click();
  assert.equal((await menuDeleted).status(), 200);
  await page.waitForURL('**/auth/login');
  await login();
  await page.waitForURL('**/dashboard/workspace');
  console.log(
    'PASS official Vben menu create/edit/delete and mandatory reauthentication after each write',
  );
  // 部门树事件必须传真实 ID，清除筛选不能残留旧 ID。
  await api(`/system/user/${target.id}`, 'put', {
    name: `已改用户${stamp}`,
    deptId: dept.id,
    status: 1,
    remark: '',
  });
  await page.goto(`${base}/system/user`);
  const filtered = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === '/api/system/user/list' &&
      new URL(r.url()).searchParams.get('deptId') === dept.id,
  );
  await page.getByText(`已改部门${stamp}`, { exact: true }).first().click();
  assert.equal((await filtered).status(), 200);
  const cleared = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === '/api/system/user/list' &&
      !new URL(r.url()).searchParams.has('deptId'),
  );
  await page.getByRole('button', { name: '全部部门（清除筛选）' }).click();
  await cleared;
  console.log('PASS department sidebar filter ID and clear');
  async function logout() {
    await page.evaluate(async () => {
      const csrf = await (
        await fetch('/api/auth/csrf', { credentials: 'include' })
      ).json();
      const response = await fetch('/api/auth/logout', {
        method: 'POST',
        credentials: 'include',
        headers: { 'X-XSRF-TOKEN': csrf.data.token },
      });
      if (!response.ok) throw new Error('测试账号切换退出失败');
      localStorage.clear();
    });
    await page.goto(`${base}/auth/login`);
  }
  await logout();
  await login('12345678', target.username);
  await page.waitForURL('**/auth/initial-password');
  const readerPass = 'T15Reader!Example42';
  await page
    .getByRole('textbox', { name: '新密码', exact: true })
    .fill(readerPass);
  await page
    .getByRole('textbox', { name: '确认新密码', exact: true })
    .fill(readerPass);
  await page.getByRole('button', { name: '确认修改' }).click();
  await page.waitForURL('**/auth/login');
  await login(readerPass, target.username);
  await page.waitForURL('**/dashboard/workspace');
  await page.goto(`${base}/system/user`);
  await page.locator('.vxe-table').first().waitFor();
  assert.equal(
    await page.getByRole('button', { name: /新增.*用户/ }).count(),
    0,
  );
  assert.equal(
    await page.getByRole('button', { name: '分配角色', exact: true }).count(),
    0,
  );
  assert.equal(
    await page.getByRole('button', { name: /修\s*改|编\s*辑/ }).count(),
    0,
  );
  const identity = await api('/user/info');
  assert.equal(identity.username, target.username);
  const denied = await page.evaluate(async () => {
    const token = JSON.parse(
      localStorage.getItem('maple-hush-admin:auth:v1') || 'null',
    )?.token;
    const statuses = [];
    for (const [method, path, data] of [
      ['get', '/system/role/list'],
      ['post', '/system/user', { username: 'denied', name: 'denied' }],
    ]) {
      const response = await fetch(`/api${path}`, {
        method: method.toUpperCase(),
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: data === undefined ? undefined : JSON.stringify(data),
      });
      statuses.push(response.status);
    }
    return statuses;
  });
  assert.deepEqual(denied, [403, 403]);
  await page.goto(`${base}/system/role`);
  await page.waitForURL('**/forbidden');
  console.log(
    'PASS ordinary role page/button restrictions, initial password flow and direct API 403',
  );
  await logout();
  await login();
  await page.waitForURL('**/dashboard/workspace');
  async function confirmDelete(path, click) {
    console.log('DELETE', path);
    const [deleted] = await Promise.all([
      page.waitForResponse(
        (r) =>
          new URL(r.url()).pathname === `/api${path}` &&
          r.request().method() === 'DELETE',
      ),
      (async () => {
        await click();
        const confirm = page
          .getByRole('button', { name: /确\s*定|确\s*认/ })
          .last();
        await confirm.click();
        await confirm.waitFor({ state: 'hidden' });
      })(),
    ]);
    assert.equal(deleted.status(), 200);
  }
  await page.goto(`${base}/system/user`);
  await recordRow(target.id)
    .getByRole('button', { name: '更多', exact: true })
    .last()
    .click();
  await confirmDelete(`/system/user/${target.id}`, () =>
    page.getByRole('menuitem', { name: /删\s*除/ }).click(),
  );
  await page.goto(`${base}/system/role`);
  await confirmDelete(`/system/role/${role.id}`, () =>
    recordRow(role.id)
      .getByRole('button', { name: /删\s*除/ })
      .last()
      .click(),
  );
  await page.goto(`${base}/system/dept`);
  await confirmDelete(`/system/dept/${dept.id}`, () =>
    page
      .locator('.vxe-body--row')
      .filter({ hasText: `已改部门${stamp}` })
      .getByRole('button', { name: /删\s*除/ })
      .last()
      .click(),
  );
  await page.goto(`${base}/system/dict`);
  await recordRow(type.id)
    .getByRole('button', { name: '管理字典项', exact: true })
    .last()
    .click();
  await page
    .getByRole('button', { name: '编辑字典项', exact: true })
    .first()
    .waitFor();
  await confirmDelete(`/system/dict/item/${items.items[0].id}`, () =>
    page
      .getByRole('button', { name: '删除字典项', exact: true })
      .first()
      .click(),
  );
  await confirmDelete(`/system/dict/type/${type.id}`, () =>
    recordRow(type.id)
      .getByRole('button', { name: '删除类型', exact: true })
      .last()
      .click(),
  );
  console.log('PASS user/role/department/dictionary UI deletion');
  assert.equal(errors.length, 0, errors.join('\n'));
  console.log('PASS seven Vben system pages load');
} catch (e) {
  console.log('URL', page.url());
  console.log('BODY', (await page.locator('body').innerText()).slice(-2500));
  throw e;
} finally {
  await browser.close();
}
