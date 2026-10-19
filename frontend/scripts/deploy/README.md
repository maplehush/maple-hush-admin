# 前端独立部署

在 `frontend/` 执行命令。此配置只启动 `frontend`，需要已经运行且容器可访问的后端；不会启动数据库、Redis 或后端。镜像使用 Node 22 和仓库固定 pnpm 11.16.0 构建 `@vben/web-antdv-next`，运行层只保留目标静态产物、Nginx 和启动脚本。Playground、docs、mock、源码及构建工具不进入运行层；完整 workspace 依赖在构建阶段保留。

## 首次启动

需要 Linux 容器引擎与 Docker Compose 2.20+：

```sh
cp .env.example .env
# 编辑 .env，必须替换 BACKEND_UPSTREAM 示例域名
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
curl -fsS http://127.0.0.1:8090/healthz
curl -fsS http://127.0.0.1:8090/api/auth/captcha
```

Windows 可在 PowerShell 使用 `Copy-Item .env.example .env`，其余 Docker 命令相同。`BACKEND_UPSTREAM` 必须是实际后端 Origin，例如 `https://api.company.example` 或受控网络内的 `http://backend-host:8080`，不能加 `/api`、尾斜杠、账号密码或查询参数。当前校验支持域名/IPv4，不支持 IPv6 字面量。HTTPS 上游开启 SNI 和 CA/主机名校验；使用内部 CA 时须挂载受控 CA bundle 并调整配置，不能关闭验证。

容器内 `localhost` 是前端容器自身；独立项目不能自动解析另一 Compose 项目的 `backend` 服务名。T18 默认后端只映射宿主机回环地址，Linux 前端容器不能直接使用宿主机回环端口；应通过已有后端 HTTPS 网关访问，或为受控共享网络提供明确的 override。Docker Desktop 的宿主机开发服务可视实际网络使用 `host.docker.internal`，不能假定该地址适用于所有服务器。根目录四服务统一网络在 T20 实施，本任务不修改根编排。

只有开发后端 `dev` 才可用回环 HTTP 调试登录，并将后端 `AUTH_ALLOWED_ORIGINS` 配为前端实际 Origin（例如 `http://localhost:8090`）。`test/prod` 必须走公开 HTTPS；Nginx 不删除 Set-Cookie 的 Secure/HttpOnly/SameSite，也不改写 Cookie Domain/Path。浏览器始终访问前端域名的 `/api`，后端仅由 Nginx 访问，前端不需要跨站 API 或通配 CORS。后台精确允许来源应包含前端公开 Origin，而不是仅填写上游后端域名。

## HTTPS 与可信代理

默认仅发布 `127.0.0.1:8090`，由宿主机/既有网关终止 TLS，参考 [网关配置](./nginx-gateway.conf.example)，替换域名、证书和端口，先执行 `nginx -t`。私钥/证书在网关受控目录，不进入前端镜像。生产前端入口必须是 HTTPS，即使后端上游已用 HTTPS，也不能替代浏览器到前端的 TLS。

`TRUSTED_PROXY_CIDR` 是前端 Nginx 实际看到的网关 IPv4 地址范围，默认 `127.0.0.1/32` 仅信容器内回环。确认宿主机网关在容器中的真实连接 IP 后设为准确 `/32`；不要直接信任所有网段。只有来自受信地址的 X-Forwarded-For / X-Forwarded-Proto 会生效；网关必须覆盖客户端传入头部。前端再覆盖传给后端的转发头，不追加任意客户端伪造链。后端是否采用这些头仍由 T18 的 `SERVER_FORWARD_HEADERS_STRATEGY` 和精确代理信任配置控制；若中间还有独立 API 网关，需要逐跳核对其覆盖规则，不能假定真实 IP 自动穿透。

`BACKEND_UPSTREAM` 与 `TRUSTED_PROXY_CIDR` 是运行时配置，启动时只替换模板中的这两个占位符，保留 `$uri` 等 Nginx 变量；修改后执行 `docker compose up -d --wait` 重建容器即可，无需重编前端。Nginx 在启动时解析上游域名；上游 IP 变化后需要重建/重载本服务，不能假定配置提供动态 DNS 刷新。

## 构建、健康与升级

```sh
# 从 frontend/ 构建指定镜像；不会停止容器或删除旧镜像
bash scripts/deploy/build-local-docker-image.sh maple-hush-frontend:review
# 或直接由 Compose 构建并重建服务
docker compose up -d --build --wait --wait-timeout 300
docker compose logs --tail 100 frontend
```

脚本在 Docker 内执行冻结锁安装与目标构建，宿主机无需先安装 pnpm；构建失败立即退出，不打印成功，也不先删旧镜像。脚本自定义镜像名若要供 Compose 使用，需与 Compose 解析的 image 一致，或用受控 override 指定 image。发布时设置明确的 `FRONTEND_IMAGE_TAG`，镜像基础版本固定到已验证 digest；保留上个已验收版本以便回滚。上游 `.github/workflows/deploy.yml` 的 Playground/FTP 工作流仅作参考，已排除出构建上下文，不用作本平台发布入口。

Nginx 以非 root 用户、只读根文件系统运行，PID 和所有临时目录写入 `/tmp` tmpfs。`/healthz` 只表示前端静态服务存活，后端故障可能使 `/api` 返回 502/503，此时前端仍 healthy；不能将此前端探针作为整个系统 readiness。后端就绪状态使用 T18 探针。日志写到标准输出/错误，Compose 限制大小；unhealthy 本身不会触发 `unless-stopped` 重启。

生产配置固定 `/api`、关闭 mock/PWA/压缩归档 zip；不在 Nginx 中重写前端 `_app-config` 的 API 地址。`index.html` 与 `_app-config-*.js` 使用 no-store，带内容哈希的 JS/CSS 等使用长期缓存，缺失资源返回 404，页面路径保留 SPA fallback。`VITE_` 变量及 `_app-config` 都是公开内容，禁止放入 OSS AccessKey、JWT 或数据库密码。

## 验收与当前限制

1. `docker compose config` 确认唯一服务为 frontend、upstream 是真实后端 Origin，端口绑定回环地址；启动后检查 UID、只读文件系统、健康及日志。
2. 通过真实 HTTPS 域名打开首页、直接刷新业务页面；检查 JS/CSS 来自目标 dist、缺失资源 404、HTML/配置不长期缓存。
3. 完成验证码、登录、首次改密（适用时）、refresh 与退出；检查 `/api` 不重复、不被 SPA fallback 吞掉，host-only Cookie 在前端域名生效，后端精确允许该前端 Origin。
4. 停后端时验证 API 错误独立于前端 healthz；恢复后可重试。核对镜像层没有 Playground、mock、源码、Node/pnpm 缓存及任何密钥。
5. 修改运行时 upstream 并重建前端，确认代理改变且静态产物保持不变；从外部确认明文 8090 不开放。完整上线验收与回滚演练在后续任务执行。

本机缺少 Docker 引擎，容器构建/启动、镜像检查、实际非 root Linux 运行与公网 HTTPS 登录尚未验收。已验证本地目标构建、Compose 配置解析、冻结锁元数据、脚本和原生 Windows Nginx；原生验证不等同于容器部署通过。实际证据见 [T19 任务卡](../../../开发文档/任务拆解/T19-前端镜像与独立部署.md)。

依据：[Nginx proxy 模块](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)、[可信代理 realip](https://nginx.org/en/docs/http/ngx_http_realip_module.html)。
