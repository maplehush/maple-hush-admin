# 服务器拉取镜像部署

已接入正式 Tag 自动部署的服务器，日常更新按[日常版本发布流程](../../开发文档/部署运维/日常版本发布流程.md)执行。以下同目录命令用于首次手动准备；流水线使用 `/opt/maplehush/config/.env`、`/opt/maplehush/compose/compose.yaml`，生成摘要锁后人工 Compose 操作也需加载 `/opt/maplehush/config/image-lock.yaml`。不要混用下面的早期单侧更新步骤与自动发布状态。

本目录配套当前前后端 Dockerfile 的镜像。CI 构建并推送到 TCR，服务器使用这里的 `compose.yaml` 和自己的 `.env` 拉取运行。生产密码、JWT、OSS 凭据从服务器 `.env` 注入，不需要 `secrets/` 或 `SPRING_CONFIG_IMPORT`。后端现有 `application.yml` / `application-prod.yml` 已能读取这些变量，无须修改应用代码。

## 自动发布脚本与服务器目录

工作流只编排检查、镜像构建和发布；版本检查、镜像复用和 SSH 交付位于 `.github/scripts/`，本目录的 `deploy.sh` 在服务器执行备份、更新与健康验证。发布 Job 检出触发 Tag 对应的提交，每次自动上传该提交的脚本，校验 SHA-256 后保存为 `/opt/maplehush/scripts/deploy-<版本>-<完整提交>-<运行号>-<重跑次数>.sh`，再以 root 执行文件并关闭标准输入。脚本不包含凭据；TCR 密码仍通过独立的标准输入传给 `docker login`。

服务器原有 `/opt/maplehush/compose/compose.yaml`、`config/.env`、`config/image-lock.yaml`、`nginx/` 和 `releases/` 均沿用。`scripts/` 会由发布过程准备为 root 所有、权限 0700，部署脚本也为 0700；原目录内其他文件不删除，不执行未知的旧脚本。上传失败、内容校验失败或部署脚本非零退出都会使发布失败；脚本和发布备份不会自动清理，日常运维需关注磁盘容量。

此次迁移不要求手动复制脚本、不修改服务器 `.env` 或现有 Compose/Nginx、不重建 MySQL/Redis。仍需现有 Secrets 和 SSH 22 端口，部署账号能非交互执行 `sudo docker login` 和 `sudo bash`；仅授权旧的 `bash -s` 精确命令时，需由管理员核对新的 `bash -c` 调用权限。不要为迁移清空已有发布记录或镜像锁。

后续手工修改服务器脚本不会成为下一次发布的来源；应在仓库修改并随新版本发布。服务器部署脚本的参数依次为版本、源码提交、前端摘要引用、后端摘要引用、运行号与重跑次数组成的标识、生产 Origin。通常只通过流水线调用，人工操作前先核对版本、摘要和数据库迁移状态。

本地回归检查：`python .github/tests/test_release_scripts.py`，需要 Python 3、Git 和 Bash；Windows 默认使用 Git for Windows Bash，可通过 `BASH` 环境变量指定其他路径。测试通过临时 Git 仓库和替代 SSH 进程运行，不连接真实 TCR 或服务器；文件校验测试中的提权目录创建使用替代实现，不能代替 Linux 权限与真实部署验收。正式 Tag 的镜像 Job 在构建前也执行同一组检查。

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

原 [运维脚本](../ops/README.md) 按根编排和文件密钥编写，不能直接当作本目录的备份命令使用。当前正式 Tag 流水线已包含已有服务器的应用更新和更新前数据库备份；首次部署、数据迁移及失败后的回退仍需单独核查处理。

真实 `.env` 已由仓库 `.gitignore` 排除。配置解析不等于容器已启动；目标服务器的镜像拉取、数据目录可写性、HTTPS、数据库初始化和真实 OSS 上传须在服务器实际验收。
