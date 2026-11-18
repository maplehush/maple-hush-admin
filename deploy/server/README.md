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

入口由独立 `nginx` 容器负责域名和 HTTPS，通过 `frontend:8080` 访问前端，再由固定 `BACKEND_UPSTREAM=http://backend:8080` 转发 `/api`。前端回环端口 `8081`（可配置）和后端回环 `8080` 保留用于本机排查；MySQL、Redis 不映射宿主机端口。修改前端宿主机端口不影响容器网关转发。

`.env` 的 `TRUSTED_PROXY_CIDR` 通过 Compose 传入前端容器，当前服务器配置要求显式填写。需要还原真实 IP 时，填写经核实的网关 IPv4 地址（`/32`）或可信网段，不是公网域名或用户 IP。示例 `172.18.0.0/16` 不会固定 Compose 网络，不能假定每台服务器均使用该网段；网关重建可能换 IP，使用精确 IP 时需重新核对。该配置也参与判断是否采用代理传来的 HTTP/HTTPS 协议信息。网关示例覆盖客户端传入的转发头，后端采纳这些头还需单独配置其代理信任规则。

## 容器网关准备和切换

将 [网关站点示例](./nginx/maplehush.conf.example) 复制到服务器 `/opt/maplehush/nginx/conf.d/maplehush.conf`，核对域名和证书路径。三个宿主机挂载目录和示例引用的有效证书须提前准备；空配置目录不会自动生成此站点，证书不存在则 HTTPS 配置不能启动。本次不自动申请证书。Certbot 仍在宿主机申请/续期，webroot 为 `/var/www/certbot`；整个 `/etc/letsencrypt` 只读挂载以保留证书符号链接。

网关暂时仅映射 `127.0.0.1:18080` 和 `127.0.0.1:18443`，不占用原公网 80/443，现有宿主机 Nginx 可继续运行。启动后在服务器本机检查（域名按实际替换）：

```sh
docker compose exec -T nginx nginx -t
curl -I --resolve admin.maplehush.cn:18080:127.0.0.1 http://admin.maplehush.cn:18080/
curl -fsS --resolve admin.maplehush.cn:18443:127.0.0.1 https://admin.maplehush.cn:18443/healthz
curl -fsS --resolve admin.maplehush.cn:18443:127.0.0.1 https://admin.maplehush.cn:18443/api/auth/captcha
```

HTTP 示例重定向指向正式 HTTPS 443，不会跳到临时 18443；HTTPS 测试直接访问 18443。若通过隧道用浏览器测试登录，`AUTH_ALLOWED_ORIGINS` 须包含浏览器实际 Origin（含临时端口），修改后重建 backend 应用配置。临时回环端口不能被外部浏览器或证书机构直接访问。

验证通过后，先释放宿主机 80/443，再把网关映射改为 `"80:80"`、`"443:443"`，执行 `docker compose up -d --no-deps nginx` 切换，并从公网正式域名验收。HTTP ACME 验证需要公网 80 的 challenge 路径到达网关；测试期间仍使用宿主机入口时，沿用原验证路由。

证书续期或站点文件更新后，执行 `docker compose exec -T nginx nginx -t` 检查，通过后再执行 `docker compose exec -T nginx nginx -s reload`；只更新挂载文件不会自动 reload。上述操作由用户在服务器完成，本次未修改远程资源。

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

排查端口调整后相应修改 curl；容器网关始终访问前端容器的 8080。前后端镜像自带 HEALTHCHECK，网关的 `depends_on` 只保证 frontend 容器先启动；前端健康不代表后端就绪，需继续从正式 HTTPS 域名验证验证码、登录、刷新、退出和需要的上传功能。Compose 使用 `environment` 注入后端变量，其中 `.env` 的 `MYSQL_USER` 映射为 Spring YAML 所需的 `MYSQL_USERNAME`。

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
