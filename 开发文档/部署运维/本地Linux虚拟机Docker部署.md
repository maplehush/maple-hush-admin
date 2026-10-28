# 本地 Linux 服务器 Docker 部署

本文整理 2026-10-26～27 的实际演练，把 Ubuntu 虚拟机作为一台正常 Linux 服务器使用，不另建带 `_vm` 后缀的配置，不引入 Jenkins。正式生产环境的差异见[正式服务器部署](./正式服务器Docker部署.md)，共用参数见[根部署说明](../../deploy/README.md)，备份与恢复见[运维手册](../../deploy/ops/README.md)。部署操作不写入任务拆解。

## 1. 先看全流程

```text
本地开发、测试 → 提交并推送 GitHub
                         ↓
服务器手动拉取代码 → Docker 构建前后端 → Compose 更新容器并等待健康
                         ↓
浏览器 → 宿主机 Nginx :80 → 127.0.0.1:8090
                         ↓
                  frontend 容器 Nginx
                         ↓ /api
                   backend:8080
                         ↓
                  mysql:3306 / redis:6379
```

宿主机 Nginx 管理公开入口，前端容器 Nginx 提供静态页面并转发 API。后端、MySQL、Redis 不映射宿主机端口。以后增加项目，可各自绑定不同的回环端口，由宿主机 Nginx 按域名分发。

### 已部署后的日常更新

以下不是首次安装命令；已有 `.env`、密钥、数据库和 Nginx 时使用：

```sh
cd /opt/maple-hush/maple-hush-admin
git pull --ff-only
docker compose up -d --build --wait --wait-timeout 600
docker compose ps
```

每条命令成功后再执行下一条。正式数据更新前先备份，方法见第 8 节。部署不需要先删除或停止容器，也不自动拉取代码。

## 2. 本次环境与实际状态

| 项目 | 本次使用值 |
| --- | --- |
| 系统 | Ubuntu 22.04.5 LTS，x86_64 |
| 资源 | 8 核、约 25 GiB 内存；根分区约 39 GiB，曾因磁盘占用高而清理 |
| Docker / Compose | Docker 29.0.2 / Compose v5.3.1 |
| 对外地址 | `http://192.168.100.128/` |
| 仓库目录 | `/opt/maple-hush/maple-hush-admin` |
| Compose 项目名 | `maple_hush_admin` |
| 前端宿主机监听 | `127.0.0.1:8090` |
| 公开入口 | 宿主机 Nginx，80 端口 |

本次已收到四服务 healthy、IP 入口可访问的终端或截图证据；用户在 2026-10-27 反馈“可以了”。登录兼容与角色选填修复已提交。期间出现过 BuildKit `frontend grpc server closed unexpectedly`，未收到最终处理日志，本文不把某项操作写成它的已确认根因或修复方案。真实公网 HTTPS、OSS、异机恢复和生产发布仍需分别验收。

首次准备时查看：

```sh
cat /etc/os-release
uname -m
free -h
df -h /
docker --version
docker compose version
docker info
```

本次 Docker 和 Compose 已安装；新服务器按实际系统使用 [Docker 安装说明](https://docs.docker.com/engine/install/ubuntu/)及 [Compose 安装说明](https://docs.docker.com/compose/install/linux/)。项目最低要求 Compose 2.24.4+，使用 `docker compose` 命令。宿主机不需要另外安装 Java、Maven、Node 或 pnpm，它们在构建镜像内运行。

## 3. 首次拉取仓库

以下克隆命令只执行一次，目标目录必须尚不存在：

```sh
mkdir -p /opt/maple-hush
cd /opt/maple-hush
git clone --branch main https://github.com/maplehush/maple-hush-admin.git
cd /opt/maple-hush/maple-hush-admin
git branch --show-current
git log -1 --oneline
```

本地修改必须先提交并推送，服务器才能拉到。`--ff-only` 只允许快进更新，分支分叉时会停止，不会在服务器自动生成合并提交。若拉取失败，先检查 `git status --short`，保留并核对服务器改动，不直接使用 `reset --hard`。

所有后续 Compose 命令均在产品仓库根执行，不进入 `frontend/` 或 `backend/` 启动另一套项目。

## 4. 首次配置环境和密钥

### 4.1 准备文件

```sh
cd /opt/maple-hush/maple-hush-admin
test -e .env || cp .env.example .env
sh backend/deploy/init-dev-secrets.sh ./secrets
```

初始化脚本只用于这次 dev 演练：保留已有文件，MySQL/Redis 使用项目约定的开发密码，JWT 随机生成。OSS 凭据文件可先为空，未配置时上传不可用。生产不能使用该脚本生成开发密码。

### 4.2 编辑根 `.env`

以复制后的模板为基础，配置以下参数，其他镜像与 OSS 公开参数保留模板或填写实际值：

```dotenv
COMPOSE_PROJECT_NAME=maple_hush_admin
SPRING_PROFILES_ACTIVE=dev
FRONTEND_IMAGE_TAG=local
BACKEND_IMAGE_TAG=local
FRONTEND_HTTP_PORT=8090
BACKEND_MEMORY_LIMIT=1g
BUILD_NETWORK=host

MYSQL_DATABASE=maple_hush_admin
MYSQL_USERNAME=maple_hush
REDIS_DATABASE=0
REDIS_KEY_PREFIX=maple_hush_admin
SECRETS_DIR=./secrets

ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_BOOTSTRAP_USERNAME=admin
AUTH_ALLOWED_ORIGINS=http://192.168.100.128
NPM_REGISTRY=https://registry.npmmirror.com
```

已有实例必须保留原项目名、库名和密钥目录，不照抄示例更名。改变 `COMPOSE_PROJECT_NAME` 会选择另一组容器、网络和数据卷。

- `ADMIN_BOOTSTRAP_ENABLED=true` 仅用于首次空库创建管理员；已有账号和密码保存在数据库，不在每次启动时重新初始化。
- `.env` 只保存非秘密配置；数据库/Redis 密码、JWT 和 OSS Key 由 `secrets/` 文件提供，具体七个文件见[生产密钥表](./正式服务器Docker部署.md#7-创建生产文件密钥)。不提交 `.env` 和真实密钥。
- 本次已验证默认容器网络访问 npm 超时，而宿主机网络可达，因此使用 `BUILD_NETWORK=host`。它只影响 Docker 构建期间的 RUN 指令，运行容器仍使用 Compose 网络。联网正常的其他机器可用 `default`。
- npm/pnpm 已支持镜像源；Docker 镜像源属于 Docker daemon 配置。宿主机的 npm、Maven、apt 源不会自动进入构建镜像。按实际问题配置，当前项目无需配置 pip、Go、Gradle 源。
- 只有通过 SSH 隧道从 Windows 打开 `http://localhost:8090` 时，才额外把该 Origin 加入允许列表。Windows 的 localhost 指 Windows 自己，不是 Linux 的回环地址。

修改已初始化 MySQL 的 secret 文件不会自动修改数据库中已有密码。修改引导开关或重建前后端容器也不会重置后台账号密码。

## 5. 一条命令构建并启动

先检查配置，不会创建容器：

```sh
docker compose config --quiet
docker compose config --services
```

应只有 `mysql`、`redis`、`backend`、`frontend` 四服务。确认后执行：

```sh
docker compose up -d --build --wait --wait-timeout 600
```

| 参数 | 含义 |
| --- | --- |
| `-d` | 容器在后台运行 |
| `--build` | 启动前构建应用镜像，复用有效缓存，把本次源码改动编入镜像 |
| `--wait` | 等待服务运行或健康检查通过 |
| `--wait-timeout 600` | 服务健康等待最多 600 秒，不是镜像构建超时 |

Compose 构建前后端、准备 MySQL/Redis、按需更新容器；后端等待两个依赖健康。数据库数据保留在 MySQL 命名卷中。普通更新不会执行删库或清空卷。

`docker compose up -d` 不保证重新构建已有镜像；`restart` 只重启旧容器。仅在已经单独构建好镜像或回退到旧镜像时使用 `--no-build`。镜像内 Maven 使用 `-DskipTests`，部署构建成功不能代替提交前的测试。

检查应用：

```sh
docker compose ps
curl -fsS http://127.0.0.1:8090/healthz
curl -fsS http://127.0.0.1:8090/api/auth/captcha
docker compose exec backend curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

`frontend` healthy 仅代表静态服务可用，仍需核对后端 readiness 和实际登录。

## 6. 首次配置宿主机 Nginx

本次使用 Ubuntu 的 `sites-available` / `sites-enabled` 站点管理方式：前者保存配置，后者通过软链接启用。若已安装并配置好，后续只更新项目时不重复安装。

在首次独立准备的 Ubuntu 服务器上：

```sh
sudo apt-get update
sudo apt-get install -y nginx
sudo systemctl enable --now nginx
```

编辑 `/etc/nginx/sites-available/maple-hush-admin`，内容如下：

```nginx
server {
    listen 80;
    server_name 192.168.100.128;

    client_max_body_size 3m;
    access_log /var/log/nginx/maple-hush-admin.access.log;
    error_log /var/log/nginx/maple-hush-admin.error.log;

    location / {
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_set_header Host $http_host;
        proxy_set_header Forwarded "";
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 10s;
        proxy_read_timeout 60s;
    }
}
```

首次启用站点，已有同名链接时跳过第一条，先检查其目标：

```sh
sudo ln -s /etc/nginx/sites-available/maple-hush-admin /etc/nginx/sites-enabled/maple-hush-admin
sudo nginx -t
sudo systemctl reload nginx
```

`nginx -t` 失败时先修正配置，不执行 reload。仅编辑文件不会自动让当前 Nginx 生效。该站点按 IP 精确匹配，不必为了它覆盖其他项目或把所有站点都改成 default_server。

分层验证：

```sh
curl -I http://127.0.0.1:8090
curl -I -H 'Host: 192.168.100.128' http://127.0.0.1
curl -I http://192.168.100.128
```

浏览器打开 `http://192.168.100.128/`。如果还是欢迎页，核对 Host、软链接、`nginx -T` 和是否已 reload；不能只凭 HTTP 200 判断命中了项目页面。直接访问 `http://192.168.100.128:8090` 不通是回环端口配置的预期行为。

以后新增项目，为各项目分配独立项目名和回环端口，再增加对应站点；有域名后用不同 `server_name` 区分，同一个 IP 的同一个入口不能同时作为多个项目首页。

## 7. 首次登录与验收

1. 获取新验证码，使用初始管理员 `admin/12345678` 登录；仅限新空库首次引导，已有实例使用实际密码。
2. 完成首次改密并重新登录；账号和新密码哈希持久化到 MySQL。
3. 检查首页、菜单、用户/角色管理、一次新增或编辑、退出后重新登录。
4. 本轮新增用户角色可选；无角色账号首次改密后只能进入基础工作台。实际可见行为须与服务器构建的版本对应。
5. 将 `.env` 中 `ADMIN_BOOTSTRAP_ENABLED` 改为 `false`，应用配置：

```sh
docker compose up -d --wait --wait-timeout 600
```

首次引导开关不是密码重置开关。密码恢复按[离线超管恢复](../../deploy/ops/README.md#5-唯一超级管理员离线恢复)处理，不通过删卷或反复重建账号解决。

IP + HTTP 登录兼容修复见[实施记录](../2026-10-27-HTTP登录兼容修复.md)。更新后关闭旧标签页，重新打开入口并强制刷新；服务端登录成功但页面仍报错时检查浏览器 Console 及后续请求，不直接认定密码错误。

当前产品网关没有转发 `/doc.html`、`/v3/api-docs`，不能把后端内部端口或文档地址误认为公开入口。真实 OSS 上传、持久化重建与完整会话刷新验收应单独记录，未执行的项目不能写为通过。

## 8. 日常更新、重启和备份

### 整体更新

先把开发机代码测试、提交并推送。服务器仍手动拉取；有需要保留的业务数据时，先执行备份：

```sh
cd /opt/maple-hush/maple-hush-admin
bash deploy/ops/backup-mysql.sh "/opt/maple-hush/backups/maple-hush-$(date +%Y%m%d-%H%M%S).sql"
git pull --ff-only
docker compose up -d --build --wait --wait-timeout 600
docker compose ps
```

备份失败先处理，不继续升级。备份脚本同时生成校验值、元数据和镜像记录，详细恢复过程见[运维手册](../../deploy/ops/README.md)。正式发布还应保留旧镜像及配置，便于应用回退。

### 只更新一个服务

```sh
# 同一项目的后端已经运行时，只更新前端
docker compose up -d --build --no-deps --wait --wait-timeout 600 frontend
# 更新后端，并检查其 MySQL、Redis 依赖
docker compose up -d --build --wait --wait-timeout 600 backend
```

### 只重启现有容器

```sh
docker compose restart backend frontend
```

只重启不会编译新代码，也不会读取改动后的容器环境；修改源码用 `--build` 更新，修改 `.env` 用 `up -d` 应用。项目更新通常不需要重启宿主机 Nginx，只有站点配置变化时才检查并 reload。

停止服务可用 `docker compose stop`，再次启动可用 `docker compose up -d --wait --wait-timeout 600`。`docker compose down` 会移除容器和项目网络但默认保留命名卷；不要附加 `-v`。

## 9. 常见问题与查看命令

| 现象 | 优先检查 |
| --- | --- |
| 修改代码后页面没变化 | 代码是否已推送、服务器是否拉取正确提交、是否重新 build、是否更新容器及刷新浏览器 |
| npm/Corepack 下载超时 | 构建容器能否访问 registry、`NPM_REGISTRY` 与 `BUILD_NETWORK` 是否生效 |
| `frontend grpc server closed unexpectedly` | BuildKit Dockerfile 解析器异常；这里的 frontend 不是业务前端，查看构建器及 Docker daemon 日志 |
| MySQL unhealthy | 查看 MySQL 日志和 healthcheck、初始化进度、磁盘及 secret；不直接删除卷 |
| `8090 address already in use` | 用 ss 查监听进程及 Docker 容器映射，排查遗留 docker-proxy，不误杀其他项目 |
| Nginx 欢迎页或 502 | 站点 Host/启用/reload、回环 8090、backend readiness，按层定位 |
| 后台密码不正确 | 数据库已有密码不会随 secret 或引导开关改变；与 MySQL 账号密码区分 |
| Navicat 连不上服务器 3306 | MySQL 默认没有发布端口；可先使用下面的容器内客户端，不开放公网数据库端口 |

```sh
docker compose logs --tail 100 mysql redis backend frontend
docker buildx ls
journalctl -u docker --since '10 minutes ago' --no-pager -n 120
ss -lntp | grep -E ':(80|443|8090)\b'
df -h /
docker system df
```

不要把 `BUILD_NETWORK=host` 当作 gRPC 解析器异常的通用修复，也不盲目重装 Docker。不要使用 `docker system prune --volumes` 等清理命令来处理数据库或构建故障。

在容器内进入 MySQL，凭据从已挂载文件读取，不在命令中写明文：

```sh
docker compose exec mysql sh -ec 'export MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"; exec mysql --user=root "$MYSQL_DATABASE"'
```

Navicat 图形化访问需另外设计回环端口加 SSH 隧道；当前默认配置不提供 `服务器IP:3306` 直连。

## 10. 换成真实服务器时

继续使用相同仓库、Compose、目录组织和更新方式，按[正式服务器手册](./正式服务器Docker部署.md)补齐独立生产凭据、prod 配置、域名与 HTTPS、备份与恢复、磁盘和服务监控。当前 HTTP + dev 是本地演练状态，不作为生产公网部署方案。
