# 正式服务器 Docker 部署

本文是未来正式服务器的操作说明，不是已完成上线的记录。2026-10-26 尚无真实服务器、域名和证书信息，安装命令和容量需按实际环境确认。学习演练见[本地 Linux 虚拟机 Docker 部署](./本地Linux虚拟机Docker部署.md)，详细 Compose 参数见[统一部署说明](../../deploy/README.md)，备份、恢复和回退见[运维说明](../../deploy/ops/README.md)。

## 1. 部署结构与前置条件

沿用根 `docker-compose.yml`，运行 `frontend/backend/mysql/redis` 四服务；服务器宿主机已有 Nginx 提供 HTTPS，反向代理到 `127.0.0.1:8090`。MySQL、Redis、backend 均不发布公网端口，不增加另一套容器服务定义。

准备并记录：

- Linux 发行版、架构、Docker Engine、BuildKit、Compose 2.24.4+、容量及部署账号。
- 可访问的生产域名、DNS、完整 TLS 证书链、私钥及续期安排。
- SSH 管理入口和 80/443 网络规则；不对公网开放 3306、6379、8080、8090。
- 独立生产 MySQL、Redis、JWT 和 OSS 凭据及其受控存储、备份。
- 已验收的源码提交、镜像版本、备份位置与负责人。

安装按实际系统参考 [Docker Engine 官方步骤](https://docs.docker.com/engine/install/)及 [Compose 插件步骤](https://docs.docker.com/compose/install/linux/)。防火墙需结合 Docker 的发布端口机制核对，不仅检查主机防火墙表面规则。

容量以预发布测量为准；源码构建的资源需求高于容器稳定运行。首次可在服务器构建，后续建议在构建环境生成已验收镜像，再按不可变版本或 digest 发布。当前 Compose 支持本地构建，镜像仓库地址、CI 推送与拉取流程需另行配置，不能假定已实现。

## 2. 准备发布代码

选择已提交并推送、经验证的版本，放到固定产品目录，例如 `/srv/maple-hush-admin`，所有 Compose 命令均在该根目录执行。保留当前版本用于回退，禁止拉取未审查变更后直接覆盖运行版本。

```sh
cd /srv/maple-hush-admin
git rev-parse HEAD
docker --version
docker compose version
docker info
```

## 3. 生产配置与文件密钥

从根 `.env.example` 创建本机 `.env`，不覆盖既有文件；生产不运行 `init-dev-secrets.sh`。

```sh
test -e .env || cp .env.example .env
```

关键参数示例，域名及 OSS 设置必须替换为实际值：

```dotenv
COMPOSE_PROJECT_NAME=maple_hush_prod
SPRING_PROFILES_ACTIVE=prod
FRONTEND_HTTP_PORT=8090
FRONTEND_IMAGE_TAG=release-实际版本
BACKEND_IMAGE_TAG=release-实际版本
MYSQL_DATABASE=maple_hush_prod
MYSQL_USERNAME=maple_hush
REDIS_DATABASE=0
REDIS_KEY_PREFIX=maple_hush_prod
SECRETS_DIR=/srv/maple-hush-secrets/prod
ADMIN_BOOTSTRAP_ENABLED=false
ADMIN_BOOTSTRAP_USERNAME=admin
AUTH_ALLOWED_ORIGINS=https://admin.example.com
```

独立受控目录中准备以下七个**无扩展名**文件，内容为相应值，不能使用开发密码：

| 文件名 | 内容 |
| --- | --- |
| mysql_root_password | 独立随机 root 密码 |
| mysql_password | 与应用账号一致的独立随机密码 |
| redis_password | 独立随机 Redis 密码 |
| auth_jwt_secret | 满足后端要求的随机 JWT 密钥，至少 32 字节强度 |
| oss_access_key_id | 最小权限 RAM 凭据 ID |
| oss_access_key_secret | 对应密钥 |
| oss_security_token | STS 凭据的 token；长期凭据可为空文件 |

OSS 的 region、Bucket、公开域名、对象前缀在 `.env` 中配置。生产与学习环境分开，不把密钥放到 `.env`、命令行参数、构建参数或 Git。文件 secret 是本地文件挂载，不是自动加密的密钥管理系统。

必须同时验证宿主机目录受控，以及容器中的 MySQL、Redis、UID 10001 的 backend 能读取各自 secret。不要直接将所有文件设为宿主机 root 专有的 0600 后就认为非 root 容器能读取；也不使用 777。具体权限根据部署账号、文件所有权和容器挂载实测决定。已有 MySQL 卷不会因为修改 secret 文件自动更新数据库密码，轮换需另行执行并验证。

## 4. 配置 HTTPS 网关

参考 [Nginx 网关模板](../../frontend/scripts/deploy/nginx-gateway.conf.example)，在服务器 Nginx 配置目录中准备站点，替换：

- `server_name`：真实域名。
- `ssl_certificate` / `ssl_certificate_key`：实际证书和私钥。
- `proxy_pass http://127.0.0.1:8090`：与 `.env` 前端端口一致。

模板在 80 上跳转 HTTPS、443 上代理整个前端站点，并保留 Host 和协议头。部署前执行 `nginx -t`，成功后按当前服务器服务管理方式 reload；不要覆盖已有其他站点。为申请证书可能需要放通的验证路径按证书方案配置，并实测自动续期。

`AUTH_ALLOWED_ORIGINS` 精确等于浏览器的生产 HTTPS Origin。prod 保持 Secure Cookie，不通过关闭 Secure、CSRF 或来源检查解决登录问题。真实客户端 IP 需要逐跳确认网关和容器的实际连接 IP，再配置准确的 `TRUSTED_PROXY_CIDR`、`TRUSTED_PROXY_REGEX` 与转发策略；保留默认配置时不要声称已取得真实客户端 IP，不信任任意外部转发头或整个私网。

## 5. 首次空库部署

只在新空库、尚未公开业务访问的首次引导窗口把 `ADMIN_BOOTSTRAP_ENABLED=true`。当前初始管理员按项目约定使用临时密码 `12345678` 并强制改密，因此此阶段仅通过受控 SSH 隧道/网关访问限制提供给维护人。常规运行保持 false。

```sh
docker compose config --quiet
docker compose config --services
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
docker compose exec backend curl -fsS http://127.0.0.1:8080/actuator/health/readiness
curl -fsS http://127.0.0.1:8090/healthz
curl -fsS http://127.0.0.1:8090/api/auth/captcha
```

检查 Flyway、MySQL、Redis 和后端日志，不能只检查前端 healthy。登录、首次改密成功后，将引导开关改回 false，执行 `docker compose up -d --wait --wait-timeout 300`，完成正式 HTTPS 验收后再开放正常访问。

prod 的接口文档关闭，启动成功日志不应包含可访问的 Knife4j/OpenAPI 链接。当前前端网关仅提供产品路由和 `/api`，不需要公开后端文档或管理探针。

## 6. 发布验收

- HTTPS 证书链、域名和跳转正确，浏览器没有证书警告。
- 验证码、登录、首次改密、菜单权限、关键写入、刷新和退出正常；Cookie 的 Secure/HttpOnly 属性符合约定。
- 真实 OSS 上传及头像显示、限制和权限经实际验证。
- 外部无法直连 MySQL/Redis/backend；可信代理未导致客户端伪造 IP。
- 重建后 MySQL 数据与 Flyway 历史仍在；备份可在独立库恢复。
- 监控磁盘、日志、内存、readiness、证书到期和备份失败。

发布记录包含 Git 提交、镜像标签/digest、Flyway 版本、配置版本和测试结果，不含秘密。未验证项列明，不因本地虚拟机演练成功而认定公网生产已通过。

## 7. 更新、备份与停止

更新前按[运维说明](../../deploy/ops/README.md)执行数据库备份，保存旧镜像及配置，确认 schema 对旧版本的兼容性。使用同一根目录、`.env` 和项目名更新：

```sh
docker compose up -d --build --wait --wait-timeout 300 frontend
docker compose up -d --build --wait --wait-timeout 300 backend
docker compose logs --tail 100 backend frontend
```

若已在独立构建环境产出镜像，发布时使用既定镜像交付方式，不在服务器重新构建同名标签。应用回退不等于数据库回退；先保留失败现场并检查迁移兼容性。停止时使用 `docker compose down`，不加 `-v`，不清理现有数据卷。正式备份还应包含受控配置、密钥和证书，并保留异机副本与恢复演练记录。
