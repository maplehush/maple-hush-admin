# 本地 Linux 虚拟机 Docker 部署

本文用于学习和演练，不作为正式服务器上线记录。共用仓库根目录的 Compose，运行 `frontend/backend/mysql/redis` 四个服务，不复制另一套服务配置。正式部署见[正式服务器 Docker 部署](./正式服务器Docker部署.md)，底层参数说明见[统一部署说明](../../deploy/README.md)。

## 1. 当前准备状态

**2026-10-27 补充**：本次实际仓库位置是 `/opt/maple-hush/maple-hush-admin`，Compose 项目名、数据库和 Redis 前缀统一使用 `maple_hush_admin`。四服务已由用户日志确认 healthy，宿主机 Nginx 已配置 `http://192.168.100.128` 入口。原前端在 IP + HTTP 下依赖 randomUUID/Web Locks，导致登录 API 成功后页面报错，不能将该状态记为登录验收通过。兼容修复见[记录](../2026-10-27-HTTP登录兼容修复.md)，需拉取修复并重建前端后，在服务器重新验收。以下初版示例中的学习环境命名和 SSH 入口是另一种隔离演练示例，不是本机实际部署参数。

2026-10-26 用户提供的终端结果：

| 项目 | 实际信息 |
| --- | --- |
| 系统 | Ubuntu 22.04.5 LTS（jammy） |
| 主机地址 | `192.168.100.128`、`192.168.100.129`；待确认 Windows 可达地址及网卡用途 |
| Docker CLI | 29.0.2 |
| Docker Compose | v5.3.1，高于本项目最低要求 |
| 当前终端账号 | root |
| 待检查 | Docker 引擎运行情况、CPU/内存/磁盘、架构、SSH 连通性及代码来源 |

Docker 和 Compose 已安装，无需重复安装。版本输出不能代替 `docker info`、配置解析或容器运行验证。本文部署命令尚未在该虚拟机执行，不能视为部署通过。后文的用户名、地址和路径示例需换成实际值；已使用 root 操作的学习环境可将代码放在 `/root/apps/maple-hush-admin`。

建议首次从 **4 核 CPU、8 GiB 内存、60 GiB 可用磁盘**开始演练；这是包含源码构建的起点建议，不是已经测得的最低要求。虚拟机需能访问镜像仓库、Maven 和前端包源；源码构建会比只运行镜像消耗更多内存和磁盘。

在 Linux 终端查看：

```sh
cat /etc/os-release
uname -m
hostname -I
free -h
df -h /
docker --version
docker compose version
docker info
```

需要 Linux Docker Engine、BuildKit 及支持本项目 `include`、`!reset`、`!override` 的 Compose，版本至少 **2.24.4**。使用 `docker compose`，不要使用旧的 `docker-compose` V1。若未安装，先根据实际发行版选择 [Docker 官方安装步骤](https://docs.docker.com/engine/install/)和 [Compose 插件安装步骤](https://docs.docker.com/compose/install/linux/)，不要混用 apt、yum 命令。

以下 Linux 命令假设当前部署账号可执行 Docker。若系统要求 sudo，应为整套操作统一安排权限，不用 `chmod 777` 解决权限问题。

## 2. Windows 如何访问虚拟机

建议先采用 **SSH 端口转发**：

```text
Windows 浏览器 localhost:8090
  → SSH 隧道
  → Linux 127.0.0.1:8090
  → frontend → backend → MySQL / Redis
```

当前 Compose 的前端只绑定 Linux 的 `127.0.0.1:8090`，所以直接打开 `http://虚拟机IP:8090` 不通是预期行为。此方式无需改 Compose，也无需向局域网开放数据库或应用端口。

先确认 Windows 可通过 SSH 连接虚拟机。桥接或可直达的 NAT 网络使用实际虚拟机 IP；若 NAT 需要端口映射，先把宿主机端口映射到虚拟机 SSH 的 22 端口。再在 **Windows PowerShell** 执行，替换用户名和地址：

```powershell
ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:8090:127.0.0.1:8090 deployuser@192.168.1.100
```

这个窗口保持打开。若 SSH 使用其他端口，加入 `-p 实际端口`。Windows 的 8090 被占用时，可将左侧监听端口改为 18090，并同步修改下文浏览器 Origin 和访问地址。

## 3. 把要测试的代码放进 Linux

在 Linux 准备一个部署目录，例如 `~/apps/maple-hush-admin`。可用 Git 拉取，或通过 SFTP 上传完整产品仓库。

注意：**远程 Git 拉取只能拿到已推送提交**。Windows 上尚未提交、推送的代码不会出现在虚拟机；演练前先确定要测试的提交或源码快照。SFTP 上传时不带 Windows 的 `node_modules`、`target`、`.idea`、`.env` 和现有密钥目录；保留两个 Dockerfile、部署脚本、锁文件以及完整构建源码。Linux 镜像构建阶段会准备自己的依赖，不要求在 Linux 宿主机另装 Java、Maven、Node。

以下命令都在 **Linux 产品仓库根目录**执行，不能在外层 `maple-hush-workspace` 或前后端子目录执行：

```sh
cd ~/apps/maple-hush-admin
git rev-parse HEAD
test -f docker-compose.yml
test -f backend/Dockerfile
test -f frontend/scripts/deploy/Dockerfile
```

若上传的源码不含 Git 元数据，另行记录源码来源和时间，不把 `git rev-parse` 失败当部署故障。

## 4. 创建学习环境配置

首次没有 `.env` 时执行；已有文件先检查，不覆盖：

```sh
test -e .env || cp .env.example .env
sh backend/deploy/init-dev-secrets.sh ./secrets
```

脚本仅供开发：MySQL/Redis 使用项目允许的开发密码，JWT 随机生成，已有密钥不覆盖；OSS 三个凭据文件可暂为空，头像和文件上传在未配置真实 OSS 时不会成功。不要上传真实密钥到聊天或 Git。

编辑根 `.env`，关键参数如下，其他参数保留模板值：

```dotenv
COMPOSE_PROJECT_NAME=maple_hush_vm_lab
SPRING_PROFILES_ACTIVE=dev
FRONTEND_HTTP_PORT=8090
MYSQL_DATABASE=maple_hush_vm_lab
MYSQL_USERNAME=maple_hush
REDIS_DATABASE=0
REDIS_KEY_PREFIX=maple_hush_vm_lab
SECRETS_DIR=./secrets
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_BOOTSTRAP_USERNAME=admin
AUTH_ALLOWED_ORIGINS=http://localhost:8090,http://127.0.0.1:8090
```

这里只用于**独立学习空库**；MySQL 容器不会连接 Windows 正在开发的数据库。首次引导成功后关闭 `ADMIN_BOOTSTRAP_ENABLED`。不要通过删除数据卷反复初始化管理员。

学习版先使用 dev + HTTP + SSH 隧道验证整套流程。它不能验收生产 Secure Cookie、真实 TLS 或公网访问；需要进一步模拟生产时使用独立 test 配置和可信 HTTPS，参照正式部署文档。

## 5. 构建并启动

```sh
docker compose config --quiet
docker compose config --services
docker compose up -d --build --wait --wait-timeout 300
docker compose ps
```

服务清单应为 `mysql`、`redis`、`backend`、`frontend`。首次构建会下载依赖，耗时取决于网络和虚拟机资源；镜像构建中的 Maven 使用 `-DskipTests`，构建成功不等于单元测试通过。

若失败，先检查日志和磁盘，不重复删卷：

```sh
docker compose logs --tail 100 mysql redis backend frontend
docker compose exec backend curl -fsS http://127.0.0.1:8080/actuator/health/readiness
curl -fsS http://127.0.0.1:8090/healthz
curl -fsS http://127.0.0.1:8090/api/auth/captcha
```

`frontend` healthy 只代表静态服务可用；后端 readiness、验证码及浏览器登录也必须正常。`50300 DEPENDENCY_UNAVAILABLE` 优先排查 MySQL/Redis 连通性和凭据；502 优先检查 backend 是否就绪及代理日志。脚本报 `\r` 等错误时检查是否以 CRLF 上传，保留 Linux shell 文件的 LF 换行。

## 6. 浏览器验收

保持 Windows SSH 隧道开启，访问 `http://localhost:8090`：

1. 获取验证码，使用学习空库初始账号 `admin/12345678` 登录。
2. 按提示完成首次改密，重新登录。
3. 检查菜单、列表、一次新增/编辑和权限页面。
4. 检查令牌刷新、退出、重新登录；未接 OSS 时不把上传失败当四服务部署失败，但明确保留上传未验收项。
5. 将根 `.env` 的 `ADMIN_BOOTSTRAP_ENABLED` 改为 `false`，执行 `docker compose up -d --wait --wait-timeout 300`。

默认前端网关没有转发 `/doc.html` 或 `/v3/api-docs`，不要假设 `localhost:8090/doc.html` 就是 Knife4j。需要在线文档时另外安排仅限本机的后端调试入口；当前根模式没有发布后端端口。

## 7. 练习重建和日常命令

记录一条测试数据后，在同一目录、相同 `.env` 和项目名下重建，再检查该数据仍在：

```sh
docker compose up -d --force-recreate --wait --wait-timeout 300
docker compose ps
```

```sh
# 更新前端；要求同一项目的后端已运行
docker compose up -d --build --wait frontend
# 更新后端及其依赖
docker compose up -d --build --wait backend
# 停止并保留卷
docker compose down
# 再次启动
docker compose up -d --wait
```

不要加 `down -v`，不要运行清理全部卷的命令。虚拟机快照可以辅助学习，但正式数据备份恢复仍需按[运维说明](../../deploy/ops/README.md)执行。

## 8. 记录实际结果

在本文之外的本地演练日志记录：Linux 版本、Docker/Compose 版本、资源、源码提交、启动结果、登录/刷新/退出、重建后数据、失败原因及处理。不要记录密码、令牌。尚未执行的项目明确写“未验证”，不把本地虚拟机结果当正式服务器上线成功。
