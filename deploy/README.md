# 仓库根目录统一部署

入口是仓库根的 [docker-compose.yml](../docker-compose.yml)。需要 Linux 容器引擎与 **Docker Compose 2.24.4+**：使用 include 加载前后端配置，并通过覆盖文件调整根模式路径及端口；不复制维护四份服务定义。最终模型只有 `frontend/backend/mysql/redis` 四服务。

## 开发环境第一次启动

在 `maple-hush-admin/` 执行：

```sh
cp .env.example .env
sh backend/deploy/init-dev-secrets.sh ./secrets
```

PowerShell 先 `Copy-Item .env.example .env`，再用 Git Bash 执行 `bash backend/deploy/init-dev-secrets.sh ./secrets`。初始化脚本只供开发，数据库/Redis 密码为约定的 `123456`，JWT 随机，已有文件不覆盖。三个 OSS 文件暂为空时仍可启动，上传返回依赖错误。真实上传继续按用户安排测试。

编辑根 `.env`，首次空库设 `ADMIN_BOOTSTRAP_ENABLED=true`，确认项目名和前端 Origin，然后执行：

```sh
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
curl -fsS http://127.0.0.1:8090/healthz
curl -fsS http://127.0.0.1:8090/api/auth/captcha
```

开发环境默认访问 `http://localhost:8090`；初始账号 `admin/12345678`，首次登录必须改密并重新登录。引导成功后恢复 `ADMIN_BOOTSTRAP_ENABLED=false`，再次 `docker compose up -d --wait`。未执行首次引导的空库会启动失败；不能靠删除现有数据卷解决配置错误。

## 整体和单侧命令

| 根目录命令 | 实际作用 |
| --- | --- |
| `docker compose up -d --build` | 创建/更新四服务；后端等待 MySQL、Redis healthy |
| `docker compose up -d --build frontend` | 仅创建/更新 frontend；需要同项目 backend 已运行，不会自动启动后端和数据服务 |
| `docker compose up -d --build backend` | 创建/更新 backend，并启动依赖的 mysql、redis；不会启动 frontend |

三个命令都可追加 `--wait --wait-timeout 300`。frontend 与 backend 无启动依赖，避免“只启动前端”隐式启动整套后端。首次部署先用整体命令；前端通过 Docker DNS 动态解析 `backend` 服务名，空项目只启动前端时静态页面可用但 API 会失败，不能据此报告系统可用。整体启动时前端可能先于后端就绪，需等待后端 readiness；前端 healthy 只表示静态服务存活。Nginx 使用 Docker DNS，缓存 5 秒后由后续请求重新解析上游，后端重建换 IP 后可恢复代理，不必为了清除旧 IP 再重建前端；换 IP 窗口可能短暂返回 API 错误。

单侧更新始终使用同一个根项目名和根 `.env`。进入 `frontend/` 或 `backend/` 使用独立 Compose 属于另一种部署模式，不是更新根项目的同一服务；不要把不同模型混用同一项目名。独立说明分别见 [前端](../frontend/scripts/deploy/README.md)、[后端](../backend/deploy/README.md)。

## 变量、路径和网络

- 根 `.env` 只含非秘密参数，`SECRETS_DIR` 相对仓库根（也可用受控绝对目录）。后端仍用原七个密钥文件和 configtree；不把密码/Key 放进根 `.env`、构建参数或前端环境。
- include 显式指定仓库根为路径基准，用 [backend-root.yml](./compose/backend-root.yml) 修正后端构建与 Redis 脚本路径，用 [frontend-root.yml](./compose/frontend-root.yml) 修正前端构建目录。固定 [include.env](./compose/include.env) 仅提供非秘密默认值，不读取子目录 `.env`；根环境有优先级。
- 根模式前端 upstream 固定为 `http://backend:8080`，即使 shell/根 `.env` 有独立模式的 BACKEND_UPSTREAM 也不会改成外部后端。浏览器仍访问同源 `/api`，不重复拼接路径。
- backend/mysql/redis 不发布任何宿主机端口；backend 仅容器内可达。frontend 仅映射 `127.0.0.1:${FRONTEND_HTTP_PORT}:8080`，默认 8090；公开访问经已有 TLS 网关，不能把明文端口直接放到公网。
- 不固定 container_name、全局网络或卷名；Compose 自动使用 `${COMPOSE_PROJECT_NAME}_default` 和 `${COMPOSE_PROJECT_NAME}_mysql_data`。不要复用项目名以免连接或覆盖另一套实例。Redis 不持久化，业务和 refresh 会话以 MySQL 为准。

## 预发布与生产 HTTPS

预发布激活 `test`，生产激活 `prod`；分别使用独立的受控密钥目录、JWT、数据库账号、Redis 前缀和 OSS 配置。不要用开发密钥初始化脚本生成生产配置。网关示例复用 [前端 HTTPS 配置](../frontend/scripts/deploy/nginx-gateway.conf.example)，替换域名、证书路径和前端端口并执行 `nginx -t`；证书/私钥留在网关受控目录，整个模型保持四服务。

后端 `AUTH_ALLOWED_ORIGINS` 填前端精确 HTTPS Origin，例如 `https://admin.company.example`。浏览器到前端的 TLS、host-only Secure Cookie 必须真实验收；内部 frontend → backend HTTP 不要求去掉 Cookie Secure 属性。根模式不对外开放后端独立 API 入口。默认忽略/不信任外部转发头；需要真实客户端 IP 时，确认 TLS 网关到前端的实际 IP 后设准确 `TRUSTED_PROXY_CIDR`，后端也只信前端实际连接 IP 后再启用 native 转发处理。不要照搬示例网段或信任任意私网。

## 同机部署两套环境

为每套环境维护独立受控环境文件，例如 `.env.a` 与 `.env.b`（不要提交），每次用 `docker compose --env-file /受控路径/.env.a ...`。至少区分：

| 参数 | A 示例 | B 示例 |
| --- | --- | --- |
| COMPOSE_PROJECT_NAME | maple_hush_a | maple_hush_b |
| FRONTEND_HTTP_PORT | 18090 | 18091 |
| SPRING_PROFILES_ACTIVE | test | test |
| MYSQL_DATABASE | maple_hush_a | maple_hush_b |
| REDIS_KEY_PREFIX | maple_hush_a | maple_hush_b |
| SECRETS_DIR | ./secrets/a | ./secrets/b |
| AUTH_ALLOWED_ORIGINS | https://a.company.example | https://b.company.example |
| OSS_BUCKET | 使用 A 的隔离 Bucket | 使用 B 的隔离 Bucket |
| OSS_OBJECT_PREFIX | vben-admin-img/a/ | vben-admin-img/b/ |

若共享同一 Bucket，至少使用独立对象前缀，公开域名仍与 Bucket 匹配；RAM 凭据权限按环境限制。命名空间隔离不能替代对象权限和实际数据测试。每套 MySQL 都有自己的容器和卷，相同库名也不会自动共享数据；仍建议显式区分库名以减少人工误操作。

## 本轮验证和后续验收

目前已通过三个目录配置解析、四服务结构/路径/端口核对和两套配置的隔离检查。本机仍没有 Docker 引擎，整体、frontend、backend 三种启动尝试都因找不到 docker_engine 失败；不等同于命令演练通过。T18/T19 的本地功能证据继续有效，不能替代根模式容器验收。

有 Docker 引擎和域名/证书后，使用独立 `test` 项目逐项验收：

1. 整体启动四服务 healthy，确认构建来自正确上下文、只有前端回环端口；从 HTTPS 首页完成验证码、登录、适用时首次改密、refresh、退出。
2. 在同一已运行项目依次执行单侧命令，核对更新范围、后端依赖与前端代理；空项目启动 frontend 不算完整系统成功；重建 backend 后确认前端通过 Docker DNS 重新解析新 IP，允许短暂失败后恢复。
3. 记录 MySQL 一条可识别数据，`docker compose up -d --force-recreate --wait` 后核对数据和 Flyway 历史仍在；Redis 重建后缓存可回源，旧验证码消失。
4. 停 MySQL/Redis，确认 backend readiness 503、liveness 200，恢复后 readiness 200；前端 healthz 不代替系统就绪。停 backend 时验证 API 错误、恢复后重试。
5. 两套项目同时启动，分别读写自己的数据库/缓存/OSS 对象，实测互不串数据、端口不冲突；核对卷和密钥挂载源确实不同。
6. 检查镜像不含密钥/源码/演示产物、真实文件权限、可信代理以及外部端口；保留实际命令与结果。真实 OSS 验收由用户执行，最终备份恢复、回滚和发布演练属于 T21。

停止使用 `docker compose down`，不附加 `-v`。根项目和独立后端项目不是同一个数据卷，切换部署模式不自动迁移数据；现有数据库/密码变更与迁移要保留备份并单独安排，不能删除旧卷。

实际记录见 [T20 任务卡](../开发文档/任务拆解/T20-仓库根目录一键部署.md)。依据：[Compose include](https://docs.docker.com/reference/compose-file/include/)、[合并覆盖规则](https://docs.docker.com/reference/compose-file/merge/)。
