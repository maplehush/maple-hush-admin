# 启动与首次登录

本页按默认端口说明。开始前已完成[环境准备](./prerequisites.md)、[下载与安装](./installation.md)和[本地配置](./configuration.md)。

## 1. 启动后端

执行位置：已经设置好环境变量的 `backend/` 终端。

```sh
java -jar admin-system-start/target/admin-system-start-0.0.1-SNAPSHOT.jar
```

预期日志包含 `Started AdminApplication` 及 `Maple Hush Admin started successfully!`。确认激活的是 dev，并且没有数据库、Redis 或 Flyway 错误。

浏览器检查：

| 地址 | 预期 |
| --- | --- |
| http://127.0.0.1:8080/actuator/health/readiness | status 为 UP |
| http://127.0.0.1:8080/api/auth/captcha | code 为 0，data 包含 captchaId 和 imageBase64 |
| http://127.0.0.1:8080/doc.html | 开发环境接口文档 |

JSON 验证码接口返回的是图片数据，不是直接显示图片的网页；登录页会负责渲染。

如果后端报唯一超级管理员状态异常，先核对是否连接了预期的新库，以及首次空库是否启用引导，不通过清空数据库解决。

## 2. 启动前端

另开终端，进入仓库的 `frontend/`：

```sh
pnpm dev:antdv-next
```

前端目标应用默认读取：

```text
frontend/apps/web-antdv-next/.env
frontend/apps/web-antdv-next/.env.development
```

开发配置已设置 `VITE_PORT=5999`、`VITE_GLOB_API_URL=/api`、`VITE_NITRO_MOCK=false`。默认代理将 `/api` 转发到 `http://127.0.0.1:8080`，不需要把数据库或 Redis 密码放入前端。

预期终端显示本地访问地址。默认访问 `http://localhost:5999/`；也可使用 `http://127.0.0.1:5999/`。一次操作流程中固定使用同一个地址，避免切换不同主机名产生两套 Cookie 和浏览器存储。

若实际启动端口不是 5999，先处理端口占用或按下节同步配置，不直接用另一个端口尝试登录。

### 自定义端口或后端地址

例如后端改为 8081、前端改为 6000：

1. 后端启动环境设置 `SERVER_PORT=8081`，`AUTH_ALLOWED_ORIGINS=http://localhost:6000,http://127.0.0.1:6000`，再启动后端。
2. 在前端启动终端设置代理目标，并用 CLI 明确指定端口：

::: code-group

```powershell [PowerShell]
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:8081'
pnpm --filter @vben/web-antdv-next dev --port 6000 --strictPort
```

```sh [Bash]
export VITE_API_PROXY_TARGET=http://127.0.0.1:8081
pnpm --filter @vben/web-antdv-next dev --port 6000 --strictPort
```

:::

当前 `vite.config.ts` 从 `process.env` 读取 `VITE_API_PROXY_TARGET`，因此在启动终端中设置；不要只把它写到 `.env.development.local` 就认为代理配置已改变。`--strictPort` 会在端口被占用时直接报错。

## 3. 首次登录并修改密码

仅当本次是空库首次引导：

1. 在前端登录页填写管理员账号，默认是 **admin**。
2. 输入临时密码 **12345678** 和页面显示的图形验证码。
3. 登录后进入首次修改密码页面，填写并确认新的个人密码。
4. 修改成功后返回登录页，获取新验证码，用新密码重新登录。
5. 确认进入工作台，并能访问当前管理员有权限的系统管理页面。

新密码须为 8～128 字符，UTF-8 编码不超过 72 字节，不使用旧密码或常见弱密码。临时密码只用于首次改密，不能当成日常固定密码。

验证码一次使用后失效；登录失败需要刷新验证码。首次改密页面保存的是短期专用凭据，若刷新页面导致凭据丢失，返回登录页重新获取。

已有数据库请使用原有账号和已保存的密码；修改启动变量不改变其密码。

## 4. 关闭首次引导

首次改密和重新登录成功后，在后端终端按 Ctrl+C 停止应用，再设置：

::: code-group

```powershell [PowerShell]
$env:ADMIN_BOOTSTRAP_ENABLED = 'false'
java -jar admin-system-start/target/admin-system-start-0.0.1-SNAPSHOT.jar
```

```sh [Bash]
export ADMIN_BOOTSTRAP_ENABLED=false
java -jar admin-system-start/target/admin-system-start-0.0.1-SNAPSHOT.jar
```

:::

使用同一个后端终端保留前面配置的其他变量；IDE 启动则修改运行配置后重启。正常账号保存在数据库，关闭引导不会删除账号。

## 5. 核对启动结果

- [ ] 后端 readiness 返回 UP。
- [ ] 前端显示验证码，接口请求经同源 /api 到达真实后端。
- [ ] 首次密码已修改，新密码可登录并进入工作台。
- [ ] 系统菜单及页面按当前账号权限展示。
- [ ] 从头像菜单退出后可重新登录。
- [ ] 首次引导已关闭，重启后使用同一个已初始化数据库和新密码。

这些是你本地运行时的检查项，不表示仅阅读教程就已经完成验收。未配置 OSS 时暂不检查上传功能。

## 停止与再次启动

前端、后端分别在各自终端按 Ctrl+C 停止。再次运行时，先确认 MySQL、Redis 可用，再启动后端和前端；已有数据库保持引导关闭。

修改前端源码通常由 Vite 热更新；修改后端源码后需要重新打包并重启 Jar。只重启旧 Jar 不会载入新编译的 Java 代码。
