# 后端独立部署

本目录配置三个服务：MySQL 8.4、Redis 7.4、Java 21 后端。请在 `backend/` 执行命令，需要 Linux 容器引擎和 Docker Compose 2.20+。本机没有 Docker 引擎时只能核对配置，不能据此认定容器部署通过。任务实际验证见 [T18 记录](../../开发文档/任务拆解/T18-后端镜像与独立部署.md)。

## 开发环境首次启动

```sh
cp .env.example .env
sh deploy/init-dev-secrets.sh
```

Windows 可在 Git Bash 执行相同命令。PowerShell 中先 `Copy-Item .env.example .env`，再用 `& 'C:/Program Files/Git/bin/bash.exe' deploy/init-dev-secrets.sh`；Docker Desktop 须启用 Linux containers。开发密钥放在个人受控目录，勿放到共享目录。脚本不覆盖已有文件，MySQL root、应用账号和 Redis 的开发密码均为 `123456`，JWT 使用随机密钥；三个 OSS 文件初始为空，未提供凭据时上传接口返回 503。

确认 `.env` 的项目名、库名、Origin 和端口。首次空库把 `ADMIN_BOOTSTRAP_ENABLED` 改为 `true`，再执行：

```sh
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
curl -fsS http://127.0.0.1:8080/actuator/health/liveness
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
curl -fsS http://127.0.0.1:8080/api/auth/captcha
```

MySQL 首次初始化、Flyway V1→V4 和首次引导自动运行，后端等待数据库和 Redis 健康。初始管理员默认 `admin/12345678`，必须改密并重新登录。引导完成后把 `ADMIN_BOOTSTRAP_ENABLED=false`，再次 `docker compose up -d --wait`。空库且引导开关关闭时，后端会明确启动失败；不要为解决这一错误删库或删卷。

## 密钥与环境

`.env` 只放非秘密参数及文件路径，`SECRETS_DIR` 指向含下列七个文件的受控目录：

| 文件 | 消费服务 | 用途 |
| --- | --- | --- |
| `mysql_root_password` | mysql | MySQL 首次初始化 root 密码 |
| `mysql_password` | mysql、backend | 专用 `MYSQL_USERNAME` 账号密码；不能填 root 账号名 |
| `redis_password` | redis、backend | Redis 认证密码 |
| `auth_jwt_secret` | backend | 至少 32 UTF-8 字节的独立 JWT 密钥 |
| `oss_access_key_id`、`oss_access_key_secret` | backend | 后端 OSS RAM 凭据 |
| `oss_security_token` | backend | 临时凭据会话 token；长期 RAM 凭据时留空 |

文件使用 UTF-8、无 BOM，写入内容时不增加换行。三个 OSS 文件即使暂时为空也需存在。Linux 目录权限用 `0700`，文件需可被容器内的非 root 用户读取（本地 Compose file secret 使用 bind mount，`uid/gid/mode` 不能替你修正宿主机权限）；开发脚本使用受限父目录配合 `0644` 文件。生产可由部署服务管理 ACL/组权限，运行前核对实际读取权限。密码/Key 不写进 `.env`、构建参数或命令行参数；也不要把 `docker inspect`/全量环境输出当作日志。

后端明确通过 `SPRING_CONFIG_IMPORT=configtree:/run/secrets/` 读取文件。Compose 把对应文件挂到 `MYSQL_PASSWORD`、`REDIS_PASSWORD`、`AUTH_JWT_SECRET`、`OSS_ACCESS_KEY_ID` 等文件名，由 Spring Boot 解析原有占位符；没有假定 Spring 自动支持 `*_FILE`。MySQL 使用官方镜像的 `MYSQL_PASSWORD_FILE`、`MYSQL_ROOT_PASSWORD_FILE`；Redis 脚本生成仅当前用户可读的临时配置，密码不会成为进程参数。Redis 禁用 RDB/AOF，以 `noeviction` 拒绝超额写入，避免内存淘汰使限流计数提前消失；需要容量扩展时同时调整 `maxmemory` 与容器上限。

测试/预发布将 `SPRING_PROFILES_ACTIVE=test`，生产设 `prod`；为每套环境设置独立的 `COMPOSE_PROJECT_NAME`、数据库/账号、Redis 前缀及 `SECRETS_DIR`，提供独立密码、JWT 和 OSS 凭据，`AUTH_ALLOWED_ORIGINS` 填精确的公开 HTTPS Origin。同一 Compose 仍使用该项目内的 mysql/redis；外置数据库/缓存需提供受控 override，修改后端连接并移除对本地服务的依赖，本文件不提供容易误连其他环境的地址兜底。发布前将镜像变量锁定为已验证的 digest，并使用明确的 `BACKEND_IMAGE_TAG`。

MySQL 命名卷已存在时，修改密钥文件或 `MYSQL_USER` 不会修改库内账号；应通过正式改密流程同时更新数据库用户与文件再重建相关服务。不要删卷来应用密码。JWT 轮换会使旧 access 失效，需安排用户重新登录。

## TLS 入口与可信代理

MySQL/Redis 不发布宿主机端口；后端默认只映射 `127.0.0.1:8080`。独立公网 API 必须由宿主机/现有网关终止 TLS，可参考 [Nginx 配置](./nginx-api.conf.example)，替换域名、证书及上游端口后先运行 `nginx -t`。前端仍应通过自己的同源 `/api` 代理请求后端，维持 host-only Cookie，不改成浏览器跨站直连。`test/prod` Cookie 使用 Secure，不能用公网 HTTP 测试登录流程。TLS 证书、私钥不进入后端镜像。

默认 `SERVER_FORWARD_HEADERS_STRATEGY=none`，伪造 X-Forwarded-For 不改变 `request.getRemoteAddr()`；这时同一网关后的客户端共用限流地址。启用真实客户端 IP 前，核对后端看到的网关连接 IP，仅信任该准确地址，再设置 `SERVER_FORWARD_HEADERS_STRATEGY=native` 和 `TRUSTED_PROXY_REGEX`，例如 `^172[.]20[.]0[.]1$`。示例地址不能直接套用：可从容器网络信息和访问日志确认。默认 `(?!)` 不匹配任何代理。Nginx 必须覆盖外部传入的转发头；不要无条件信任任意私网 IP 或任意请求头。Tomcat 在可信连接上处理转发头后，现有 `getRemoteAddr()` 限流和审计自然使用真实 IP，无需再写一套 IP 解析工具。

## 健康、重建和故障验收

`liveness` 仅看进程状态；`readiness` 同时检查 MySQL 与 Redis。Docker healthcheck 使用 readiness，依赖不健康时标记 unhealthy；`unless-stopped` 只在进程退出时重启，不会因单纯 unhealthy 重启。网关/运维应按 readiness 停止新流量；普通 Nginx OSS `proxy_pass` 不会自动消费该探针，未配置流量控制时会继续向不健康后端发送请求。

在专用验收项目内逐项执行，避免影响其他实例：

1. 通过前端/HTTPS 入口完成验证码、首次改密和重新登录，查询 `/api/user/info`，保存一条可识别的数据。
2. `docker compose up -d --force-recreate --wait`，确认数据和 Flyway 历史仍在，原会话能按规则读取；该命令保留命名卷。
3. `docker compose stop redis`，稍等健康轮询后检查 readiness 为 503、liveness 为 200，验证码/登录返回依赖错误；再 `docker compose start redis`，等待恢复 healthy，已登录用户快照可从 MySQL 重建。Redis 重建后旧验证码消失。
4. `docker compose stop mysql`，确认 readiness 为 503、liveness 仍为 200；`docker compose start mysql` 后恢复。故障期间不要启动新的后端实例来代替存活性检查。
5. `docker compose exec backend id` 应显示 UID/GID 10001，`docker compose exec backend sh -c 'ls -l /app; test ! -d /root/.m2'` 只含运行 Jar；用 `docker history --no-trunc` 和导出镜像层检查没有密钥、源码、测试、构建缓存和 `.env`。真实密钥不应出现在 history/inspect 中。
6. 从外部主机确认 3306、6379、明文 8080 均不可达；实际演练 HTTPS API 和 Cookie。真实 OSS 上传、读取 ACL 和清理仍按用户安排验收。

停止用 `docker compose down`，不附加 `-v`。`down -v` 会删除数据库命名卷。后续根目录编排由 T20 实施，当前配置没有固定 `container_name`、全局卷名或网络名，可按 Compose 项目隔离多套实例。

依据：[Compose secrets](https://docs.docker.com/compose/how-tos/use-secrets/)、[依赖健康等待](https://docs.docker.com/compose/how-tos/startup-order/)、[Spring Boot configuration trees](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.configtree)。
