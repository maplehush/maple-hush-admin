# 服务器拉取镜像部署

本目录配套当前前后端 Dockerfile 的镜像。CI 构建并推送到 TCR，服务器使用这里的 `compose.yaml` 和自己的 `.env` 拉取运行。生产密码、JWT、OSS 凭据从服务器 `.env` 注入，不需要 `secrets/` 或 `SPRING_CONFIG_IMPORT`。后端现有 `application.yml` / `application-prod.yml` 已能读取这些变量，无须修改应用代码。

## 准备

将 [compose.yaml](./compose.yaml) 和 [.env.example](./.env.example) 复制到服务器同一部署目录，例如 `/opt/maplehush/`。以下命令均在该目录执行；没有 `.env` 时再从示例创建，不覆盖已有配置：

```sh
test -e .env || cp .env.example .env
chmod 600 .env
```

编辑 `.env`，填写实际密码、JWT 密钥、公开 HTTPS Origin 和所需 OSS 凭据。JWT 可使用 `openssl rand -hex 32` 生成，填写其输出；不要把命令本身写入变量。前后端版本分别选择 TCR 中存在且相互兼容的标签。未配置 OSS 凭据时上传不可用，其余功能可以启动。

首次空库设 `ADMIN_BOOTSTRAP_ENABLED=true` 创建 `admin`，初次登录使用临时密码 `12345678` 并按页面要求改密。后续可改为 `false`，再次执行 `up -d` 应用；已有正常管理员时不会重复创建或重置密码。非空但管理员关联异常的数据库须按原超管恢复流程处理。

宿主机 Nginx 负责域名和 HTTPS，转发到 `127.0.0.1:8081`（或 `.env` 中指定端口）。前端容器监听 **8080**，通过固定 `BACKEND_UPSTREAM=http://backend:8080` 转发 `/api`；后端只映射回环 `8080`，MySQL、Redis 不映射宿主机端口。默认代理信任配置不用于还原真实客户端 IP，如需此能力须另行核实代理地址后配置。

## 检查并启动

```sh
docker login ccr.ccs.tencentyun.com
docker compose config --quiet
docker compose config --images
docker compose pull
docker compose up -d --wait --wait-timeout 600
docker compose ps
curl -fsS http://127.0.0.1:8081/healthz
curl -fsS http://127.0.0.1:8081/api/auth/captcha
```

端口调整后相应修改 curl 和宿主机 Nginx。前后端镜像自带 HEALTHCHECK；前端健康只表示静态服务可用，需继续从正式 HTTPS 域名验证验证码、登录、刷新、退出和需要的上传功能。Compose 使用 `environment` 注入后端变量，其中 `.env` 的 `MYSQL_USER` 映射为 Spring YAML 所需的 `MYSQL_USERNAME`。

## 独立更新

只升级前端，修改 `.env` 中 `FRONTEND_VERSION` 后：

```sh
docker compose pull frontend
docker compose up -d --no-deps --wait --wait-timeout 600 frontend
```

只升级后端，先确认数据库迁移和前端接口兼容并完成备份，修改 `BACKEND_VERSION` 后：

```sh
docker compose pull backend
docker compose up -d --no-deps --wait --wait-timeout 600 backend
```

单侧更新要求其他依赖已正常运行；首次部署用整体启动。此模式不执行源码构建，无需服务器 NPM 源、构建网络或前后端构建标签参数。

## 数据与旧部署

MySQL 使用 `${DATA_ROOT}/mysql`，Redis 使用 `${DATA_ROOT}/redis` 并开启 AOF。已有实例保持原项目名、数据路径及数据库账号密码；修改 `.env` 不会自动修改已初始化数据库中的密码。本目录的绑定挂载与原根编排的 MySQL 命名卷不同，不会自动迁移旧数据；从旧部署切换须先备份并安排导入，不能直接切目录启动就视为升级成功。

原 [运维脚本](../ops/README.md) 按根编排和文件密钥编写，不能直接当作本目录的备份命令使用。此处只交付镜像运行配置，不包含服务器自动部署、数据迁移或发布回退自动化。

真实 `.env` 已由仓库 `.gitignore` 排除。配置解析不等于容器已启动；目标服务器的镜像拉取、数据目录可写性、HTTPS、数据库初始化和真实 OSS 上传须在服务器实际验收。
