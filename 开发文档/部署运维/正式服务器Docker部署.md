# 正式服务器 Docker 部署：从空白 Ubuntu 开始

**逐步执行入口：** [Ubuntu 24.04 从零部署执行清单](./Ubuntu24.04从零部署执行清单.md)。按清单的 15 步推进，本页提供首次手工部署的基础安装命令；最终采用[测试与生产流水线部署方案](./测试与生产流水线部署方案.md)，GitHub Actions 构建镜像，测试自动部署、生产人工确认后部署。流水线尚未实现。

本文从**尚未安装 Git、Docker、Compose、Nginx，也没有项目目录**的服务器开始，直到首次上线和日常更新。本页的手动拉取、源码构建与更新用于首次部署演练；接入流水线后，构建移到 runner，服务器拉取已验收镜像。不引入 Jenkins。

操作基线：Ubuntu 24.04 LTS（noble）、amd64（x86_64）、systemd。正式服务器统一使用这个版本；其他发行版或 ARM 机器不属于本手册的操作基线。本地演练记录见[本地 Linux 服务器手册](./本地Linux虚拟机Docker部署.md)，共用 Compose 说明见[根部署说明](../../deploy/README.md)。

这里指定的是宿主机系统。当前后端基础镜像仍为 `eclipse-temurin:21-jre-jammy`，镜像内部的 Ubuntu Jammy 与宿主机 Noble 相互独立，不能因为宿主机使用 24.04 就把镜像内 apt 源改成 noble。

下面的初始化命令在有权限的运维会话执行；普通 sudo 账号可先 `sudo -i`，已经是 root 则跳过。命令分块按顺序执行，一步成功再执行下一步。文中的 `admin.example.com`、公网 IP、企业镜像仓库和 OSS 参数必须改为实际值。本文是待执行的正式环境手册，不表示已在全新生产服务器安装验证。

## 0. 最后会得到什么

```text
浏览器 HTTPS :443
    → 宿主机 Nginx（域名、证书和多个项目入口）
    → 127.0.0.1:8090
    → frontend 容器 Nginx（静态页面、同源 /api）
    → backend:8080 → mysql:3306 / redis:6379
```

| 安装位置 | 需要的东西 |
| --- | --- |
| 宿主机 | CA 证书、curl、Git、OpenSSL、编辑器、Python 3（配置校验）、Docker Engine、Buildx、Compose 插件、Nginx |
| HTTPS 自动签发时 | snapd 与 Certbot；也可使用已有受控证书交付方式 |
| Docker 镜像内 | Java 21、Maven、Node/pnpm、MySQL、Redis、前端 Nginx |
| 无需另装到宿主机 | Java、Maven、Node、npm/pnpm、MySQL、Redis、Jenkins、Go、Gradle |

首次需要“安装 + 配置 + 密钥 + HTTPS”；准备完成后，源码构建模式的部署命令只有一条：

```sh
docker compose up -d --build --wait --wait-timeout 600
```

它不自动拉取 Git、不重置数据库、不代替备份和浏览器验收。

## 1. 检查系统、磁盘、时间和网络

```sh
cat /etc/os-release
uname -m
free -h
df -h /
timedatectl
```

源码构建需要额外磁盘和内存。不要只按容器稳态占用分配容量；预留基础镜像、依赖缓存、构建产物、数据库和备份空间，实际容量以演练结果为准。

云安全组和服务器防火墙：SSH 实际端口仅允许维护来源；HTTP 80 用于证书验证与跳转；HTTPS 443 在首次引导时仅允许维护来源，验收后开放给实际业务访问范围。不要对外开放 3306、6379、8080、8090。先确认 SSH 端口和管理访问，再调整防火墙，不直接执行可能阻断 SSH 的通用重置命令。

如系统时间不同步：

```sh
timedatectl set-ntp true
timedatectl status
```

## 2. Ubuntu 软件源与基础工具

### 2.1 换源前先识别文件

```sh
cat /etc/os-release
ls -l /etc/apt/sources.list.d/ubuntu.sources
```

确认系统代号为 `noble`。Ubuntu 24.04 使用 DEB822 格式的 `ubuntu.sources`；云厂商已经提供可用的内网源时可以保留。以下使用清华 TUNA 的 Ubuntu 镜像，**保留官方 security 更新源**；普通归档镜像和安全更新不混为一谈。若 HTTPS 报缺少 CA，先用已有源执行 `apt-get update`、`apt-get install -y ca-certificates`，不关闭 TLS 校验。若该文件不存在，先核对系统镜像与现有 apt 配置，不直接套用其他 Ubuntu 版本的源。

备份并配置 **24.04 / noble 的 DEB822 源**：

```sh
cp -an /etc/apt/sources.list.d/ubuntu.sources /etc/apt/sources.list.d/ubuntu.sources.before-maple
cat > /etc/apt/sources.list.d/ubuntu.sources <<'EOF'
Types: deb
URIs: https://mirrors.tuna.tsinghua.edu.cn/ubuntu/
Suites: noble noble-updates noble-backports
Components: main restricted universe multiverse
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg

Types: deb
URIs: http://security.ubuntu.com/ubuntu/
Suites: noble-security
Components: main restricted universe multiverse
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
EOF
```

参考：[TUNA Ubuntu 帮助](https://mirrors.tuna.tsinghua.edu.cn/help/ubuntu/)。如果源文件布局不同，先按系统代号调整；不全局 sed 替换所有第三方源、不启用 proposed。

### 2.2 安装本项目需要的基础工具

```sh
apt-get update
apt-get install -y ca-certificates curl gnupg git openssl python3 nano nginx
systemctl enable --now nginx
```

不在这里运行无差别系统升级。新服务器需要的补丁和重启按维护窗口安排；如果已存在服务，不把本手册当作重装或卸载指令。

## 3. 安装 Docker、Buildx 和 Compose

### 3.1 Docker 安装源不等于 Docker 镜像源

Docker CE apt 仓库用于下载引擎和插件；`registry-mirrors` 用于拉容器镜像，两者独立。本节采用仓库安装方式，不执行 `curl ... | sh`，也不混装 Ubuntu 的 docker.io 与 Docker CE 的 containerd/runc。

先确认是否确实是空白服务器：

```sh
dpkg -l docker.io docker-ce containerd containerd.io runc 2>/dev/null
```

若已有上述组件或容器，先核对来源，不执行卸载。空白机器继续：

```sh
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
cat > /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu
Architectures: $(dpkg --print-architecture)
Suites: ${VERSION_CODENAME}
Components: stable
Signed-By: /etc/apt/keyrings/docker.asc
EOF
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
```

官方仓库可达时，可以将上面的 key 和仓库基址改为 `https://download.docker.com/linux/ubuntu`；不要同时写两份相同 Docker 仓库。

验证并记录实际版本：

```sh
docker version
docker info
docker buildx version
docker buildx ls
docker compose version
```

Compose 最低 2.24.4+。使用 `docker compose` 插件命令，不另装老版 docker-compose V1，也不单独升级/替换 runc。日常 Docker 权限由运维账号管理；docker 组具备相当于 root 的控制能力，不为任何用户随意授权。

参考：[Docker Ubuntu 安装](https://docs.docker.com/engine/install/ubuntu/)、[TUNA Docker CE 安装源](https://mirrors.tuna.tsinghua.edu.cn/help/docker-ce/)、[Compose 插件](https://docs.docker.com/compose/install/linux/)。

## 4. 容器镜像从哪里拉

### 4.1 先明确正式镜像来源

不要复制本地机器中长串、已经失效或用途不明的公共 mirror 地址。阿里云当前官方说明：个人镜像加速器停止同步最新镜像，且仅限个人开发用途；不能作为本项目生产镜像供应方案。参考[官方加速器说明](https://help.aliyun.com/zh/acr/user-guide/accelerate-the-pulls-of-docker-official-images)。

选择可维护的路径：

- Docker Hub 官方源在部署网络可达：直接拉取项目声明的镜像，记录版本和 digest。
- Docker Hub 不可达：把经过核对的基础镜像同步到企业有权限使用的 ACR/Harbor 等仓库，再修改根 `.env` 的各个镜像地址。企业仓库地址和权限来自实际账号，本文不能替你编造一个公共地址。
- 企业已有合法可用的 Docker Hub pull-through cache：按 4.3 配置，而不是把企业私有仓库地址当成通用 mirror。

### 4.2 企业仓库同步的完整命令

在**能够访问原始仓库、已获得企业仓库推送权限的构建机器**执行一次。构建机器的 Docker 需支持 `push --platform`（API 1.46+）；先在仓库控制台创建对应命名空间与仓库。下面 `registry.cn-beijing.aliyuncs.com/company-maple` 只是地址格式示例，必须替换成实际实例地址及命名空间：

```sh
REGISTRY_BASE='registry.cn-beijing.aliyuncs.com/company-maple'
docker login "${REGISTRY_BASE%%/*}"
(
while read -r source target; do
    docker pull --platform linux/amd64 "$source" || exit 1
    docker tag "$source" "$REGISTRY_BASE/$target" || exit 1
    docker push --platform linux/amd64 "$REGISTRY_BASE/$target" || exit 1
done <<'EOF'
mysql:8.4 mysql:8.4
redis:7.4-alpine redis:7.4-alpine
maven:3.9.9-eclipse-temurin-21 maven:3.9.9-eclipse-temurin-21
eclipse-temurin:21-jre-jammy java:21-jre-jammy
node:22-bookworm-slim node:22-bookworm-slim
nginx:1.30-alpine nginx:1.30-alpine
docker/dockerfile:1 dockerfile:1
EOF
)
```

任何一步失败，先修复该镜像，不认为后续镜像已完成。指定平台 push 不保留多架构索引及其他 attestations，不把它当完整多架构复制；需要保留完整签名/证明时按企业制品复制流程执行。记录源和目标 digest、来源与审核结果，发布时优先使用已验收的不可变引用；不要把未经核对的公共镜像重新推送后就当可信。参考 [Docker push 平台选项](https://docs.docker.com/reference/cli/docker/image/push/)。

在正式服务器使用仅拉取权限登录企业仓库：

```sh
docker login registry.cn-beijing.aliyuncs.com
```

按第 5 节克隆代码、创建根 `.env` 后，将镜像项改为实际企业仓库地址：

```dotenv
MYSQL_IMAGE=registry.cn-beijing.aliyuncs.com/company-maple/mysql:8.4
REDIS_IMAGE=registry.cn-beijing.aliyuncs.com/company-maple/redis:7.4-alpine
MAVEN_IMAGE=registry.cn-beijing.aliyuncs.com/company-maple/maven:3.9.9-eclipse-temurin-21
JAVA_IMAGE=registry.cn-beijing.aliyuncs.com/company-maple/java:21-jre-jammy
NODE_IMAGE=registry.cn-beijing.aliyuncs.com/company-maple/node:22-bookworm-slim
NGINX_IMAGE=registry.cn-beijing.aliyuncs.com/company-maple/nginx:1.30-alpine
DOCKERFILE_FRONTEND=registry.cn-beijing.aliyuncs.com/company-maple/dockerfile:1
```

特别注意 `docker/dockerfile:1` 也是一张需要拉取的解析器镜像，不是业务前端；现在根 Compose 和独立配置均通过 `BUILDKIT_SYNTAX` 接受 `DOCKERFILE_FRONTEND`。换完业务基础镜像却漏掉解析器，仍可能在 Dockerfile 第一行访问 Docker Hub。参考[Docker 自定义解析器](https://docs.docker.com/build/buildkit/frontend/)。

### 4.3 仅在企业提供 pull-through cache 时配置 daemon

这一段是替代路径，已使用 4.2 的直接企业镜像地址则无需再配置。编辑 `/etc/docker/daemon.json`，将下面格式中的地址替换为企业实际提供的 HTTPS mirror，并与已有配置合并：

```json
{
  "registry-mirrors": ["https://docker-cache.company.example"],
  "log-driver": "json-file",
  "log-opts": {"max-size": "10m", "max-file": "3"}
}
```

`docker-cache.company.example` 不是公共服务，未获得真实地址时不要填入。已有 daemon.json 先备份，不覆盖其他运行参数。全新、尚未部署项目的服务器配置后：

```sh
python3 -m json.tool /etc/docker/daemon.json > /dev/null
dockerd --validate --config-file=/etc/docker/daemon.json
systemctl restart docker
docker info
```

已有业务机器重启 Docker 需另行维护安排。不要为换源加入 `insecure-registries`、关闭 HTTPS 或使用未审查的镜像列表。自建 registry cache 只影响 Docker Hub，不替代 apt/npm/Maven 网络配置。参考[Docker mirror 配置](https://docs.docker.com/docker-hub/image-library/mirror/)。

## 5. 建立干净的部署工作区

```sh
install -d -m 0755 /opt/maple-hush
install -d -m 0700 /opt/maple-hush/backups /opt/maple-hush/releases
cd /opt/maple-hush
git clone --branch main https://github.com/maplehush/maple-hush-admin.git
cd /opt/maple-hush/maple-hush-admin
git status --short
git log -1 --oneline
test -e .env || cp .env.example .env
```

clone 只在目录不存在时执行。私有仓库通过部署只读密钥或凭据管理认证，不把 token 嵌入 clone URL 或文档。服务器无需拷贝开发机的 node_modules、target、IDE 配置和密钥。所有 Compose 命令都在产品仓库根执行。

## 6. 根 `.env` 与构建依赖源

编辑刚复制的根 `.env`，其余公开镜像与 OSS 参数填写实际值：

```dotenv
COMPOSE_PROJECT_NAME=maple_hush_prod
SPRING_PROFILES_ACTIVE=prod
FRONTEND_HTTP_PORT=8090
BACKEND_MEMORY_LIMIT=1g
FRONTEND_IMAGE_TAG=release-20261027-01
BACKEND_IMAGE_TAG=release-20261027-01
MYSQL_DATABASE=maple_hush_prod
MYSQL_USERNAME=maple_hush
REDIS_DATABASE=0
REDIS_KEY_PREFIX=maple_hush_prod
SECRETS_DIR=/opt/maple-hush-secrets/prod
ADMIN_BOOTSTRAP_ENABLED=false
ADMIN_BOOTSTRAP_USERNAME=admin
AUTH_ALLOWED_ORIGINS=https://admin.example.com

BUILD_NETWORK=default
NPM_REGISTRY=https://registry.npmmirror.com
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
UBUNTU_APT_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/ubuntu
DOCKERFILE_FRONTEND=docker/dockerfile:1
```

若采用企业镜像仓库，把第 4.2 节的镜像项一并填入，特别是用实际私有 `DOCKERFILE_FRONTEND` 替换示例中的官方地址。项目名、库名和密钥目录一旦已有数据便保持稳定。release 标签是有效格式示例，每次发布换新标签并保留旧镜像。

### 各种源到底在哪里生效

| 项目 | 本手册配置 | 影响范围 |
| --- | --- | --- |
| Ubuntu 主机 apt | 第 2 节系统源文件 | 安装 Git、Docker 依赖、Nginx 等主机软件 |
| Docker CE apt | 第 3 节 docker.sources | 安装引擎、containerd、Buildx、Compose |
| 容器镜像 | 第 4 节和 `*_IMAGE` / `DOCKERFILE_FRONTEND` | 拉取基础镜像和 Dockerfile 解析器 |
| npm/pnpm | `NPM_REGISTRY` | Corepack 下载固定版本 pnpm，pnpm 安装前端依赖 |
| Maven Central | `MAVEN_MIRROR_URL` | 通过仓库内 `backend/deploy/maven-settings.xml` 和 Dockerfile `mvn -s` 作用于构建容器 |
| 后端运行镜像 apt | `UBUNTU_APT_MIRROR` | Java Jammy 镜像构建时安装 curl/CA/字体；保留 security 源 |

只在宿主机执行 `npm config set registry` 或编辑 `~/.m2/settings.xml`，不会自动改变本项目 Dockerfile 的构建。前端 Node 和 Nginx 阶段当前没有 apt/apk 安装步骤，无需为了这个项目另外配置 Debian/Alpine 软件源。

Maven 默认官方地址为 `https://repo.maven.apache.org/maven2`，国内镜像示例为阿里云公共代理；只镜像 Central，不覆盖未来可能增加的私有仓库。镜像站新版本同步延迟出现 404 时，先核对依赖版本与官方源；不是改低依赖版本或关闭校验。参考[阿里云 Maven 镜像](https://developer.aliyun.com/mirror/maven)、[Maven settings](https://maven.apache.org/settings.html)。

`UBUNTU_APT_MIRROR` 留空时保持 Java 镜像原始源；仅适用于当前 Ubuntu Jammy 运行镜像，更换其他系统基础镜像后需复核。以上源参数只填公开 HTTPS URL，不放账户密码；私有依赖仓库凭据必须通过另外设计的 BuildKit secret 传入，不用 ARG/ENV。

### 先测可达性，再决定是否使用 host 构建网络

宿主机测试包元数据和实际 Maven parent：

```sh
curl -I --connect-timeout 15 --max-time 30 https://registry.npmmirror.com/pnpm/11.16.0
curl -I --connect-timeout 15 --max-time 30 https://maven.aliyun.com/repository/public/org/springframework/boot/spring-boot-starter-parent/4.1.1/spring-boot-starter-parent-4.1.1.pom
```

在创建 `.env` 并配置好镜像后，可用下面的预检查一次拉取六个基础镜像与解析器；这不是日常更新必须分开执行的步骤：

```sh
python3 - <<'PY'
import json
import subprocess
model = json.loads(subprocess.check_output(['docker', 'compose', 'config', '--format', 'json']))
images = [model['services'][s]['image'] for s in ('mysql', 'redis')]
for s, keys in [('backend', ('MAVEN_IMAGE', 'JAVA_IMAGE', 'BUILDKIT_SYNTAX')),
                ('frontend', ('NODE_IMAGE', 'NGINX_IMAGE', 'BUILDKIT_SYNTAX'))]:
    images.extend(model['services'][s]['build']['args'][key] for key in keys)
for image in dict.fromkeys(images):
    subprocess.run(['docker', 'pull', image], check=True)
node_image = model['services']['frontend']['build']['args']['NODE_IMAGE']
registry = model['services']['frontend']['build']['args']['NPM_REGISTRY'].rstrip('/')
subprocess.run(['docker', 'run', '--rm', node_image, 'node', '-e',
    'fetch(process.argv[1]).then(r=>{console.log(r.status);if(!r.ok)process.exit(1)}).catch(e=>{console.error(e);process.exit(1)})',
    registry + '/pnpm/11.16.0'], check=True)
PY
```

如果宿主机可达而容器超时，先检查 Docker DNS、路由和防火墙；可以用同一个实际 Node 镜像加 `--network host` 对照验证。仅确认 host 可达时在根 `.env` 使用 `BUILD_NETWORK=host`，不把当前虚拟机的问题假定为每台新服务器都有。该参数只影响构建 RUN 网络，不会让运行中的 MySQL/Redis/backend 暴露到宿主机，也不修复 Dockerfile 解析器崩溃。

## 7. 创建生产文件密钥

生产不运行 `init-dev-secrets.sh`，不使用开发密码。先创建受控目录，随机生成四个彼此独立的值；存在的文件保留：

```sh
install -d -m 0700 /opt/maple-hush-secrets/prod
for name in mysql_root_password mysql_password redis_password auth_jwt_secret; do
    if [ ! -e "/opt/maple-hush-secrets/prod/$name" ]; then
        (umask 077; openssl rand -hex 32 > "/opt/maple-hush-secrets/prod/$name")
        chmod 0644 "/opt/maple-hush-secrets/prod/$name"
    fi
done
for name in oss_access_key_id oss_access_key_secret oss_security_token; do
    if [ ! -e "/opt/maple-hush-secrets/prod/$name" ]; then
        install -m 0644 /dev/null "/opt/maple-hush-secrets/prod/$name"
    fi
done
```

父目录 0700 限制宿主机普通用户；文件单独挂载到容器后需让非 root Redis、backend UID 10001 等读取，因此不是把所有文件都改为 0600。不要使用 777；实际权限和容器可读性需检查。若改用非 root 部署账号，明确调整父目录所有者，不随意扩大目录权限。

| 无扩展名文件 | 内容 |
| --- | --- |
| mysql_root_password | MySQL root 随机密码 |
| mysql_password | 应用数据库账号随机密码 |
| redis_password | Redis 随机密码 |
| auth_jwt_secret | 随机 JWT 密钥 |
| oss_access_key_id | 最小权限 RAM 的 ID |
| oss_access_key_secret | 对应 Key |
| oss_security_token | STS token；使用长期凭据时保留空文件 |

通过受控编辑器填写 OSS 凭据，不在聊天、Git 或 shell history 中写明文：

```sh
nano /opt/maple-hush-secrets/prod/oss_access_key_id
nano /opt/maple-hush-secrets/prod/oss_access_key_secret
nano /opt/maple-hush-secrets/prod/oss_security_token
```

根 `.env` 的 region、Bucket、公开域名、对象前缀需与真实 OSS 匹配；文件密钥备份独立受控保存。已有 MySQL 卷不会因为修改 secret 文件而自动修改数据库密码，轮换需要单独流程。

## 8. 域名、Nginx 与 HTTPS（首次准备）

### 8.1 DNS 与证书客户端

把真实域名 A 记录指向服务器公网 IPv4；有 AAAA 记录时也必须指向可达的 IPv6，否则先修正。证书 HTTP-01 验证要求域名的 80 端口可从验证网络访问。没有域名和 HTTPS 时只能先按本地 IP + dev 手册演练，不标记为 prod 上线。

本手册采用 Ubuntu 官方推荐的 snap Certbot。已有受控证书可使用公司签发流程，不需要再安装另一套客户端；全新机器使用：

```sh
apt-get install -y snapd
systemctl enable --now snapd.socket
snap install --classic certbot
/snap/bin/certbot --version
```

snap 自身访问 Snap Store，不受前述 apt/npm mirror 控制。不能访问时按公司已有证书交付方式处理，不随意切换来历不明的证书脚本。参考[Ubuntu 获取 TLS 证书](https://ubuntu.com/server/docs/how-to/security/obtain-tls-certificates/)。

### 8.2 先提供验证路径，再申请证书

```sh
install -d -m 0755 /var/www/letsencrypt
nano /etc/nginx/sites-available/maple-hush-admin
```

首次写入下面的临时 HTTP 站点，替换域名；它暂不公开应用：

```nginx
server {
    listen 80;
    server_name admin.example.com;
    location ^~ /.well-known/acme-challenge/ {
        root /var/www/letsencrypt;
    }
    location / { return 404; }
}
```

启用并检查。软链接已存在时先核对目标，跳过 ln，不强制覆盖其他站点：

```sh
ln -s /etc/nginx/sites-available/maple-hush-admin /etc/nginx/sites-enabled/maple-hush-admin
nginx -t
systemctl reload nginx
/snap/bin/certbot certonly --webroot -w /var/www/letsencrypt -d admin.example.com
```

按提示填写真实维护邮箱和条款选择，确认签发成功。证书通常位于 `/etc/letsencrypt/live/admin.example.com/`，以 Certbot 实际输出为准。参考 [Certbot webroot](https://eff-certbot.readthedocs.io/en/stable/using.html#webroot)。

### 8.3 配置正式 HTTPS 代理

用下面内容替换**本项目**站点，不覆盖全局 nginx.conf。域名和证书路径同时替换：

```nginx
server {
    listen 80;
    server_name admin.example.com;
    location ^~ /.well-known/acme-challenge/ {
        root /var/www/letsencrypt;
    }
    location / { return 301 https://$host$request_uri; }
}
server {
    listen 443 ssl;
    server_name admin.example.com;
    ssl_certificate /etc/letsencrypt/live/admin.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/admin.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 3m;
    access_log /var/log/nginx/maple-hush-admin.access.log;
    error_log /var/log/nginx/maple-hush-admin.error.log;
    location / {
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_set_header Host $http_host;
        proxy_set_header Forwarded "";
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto https;
        proxy_connect_timeout 10s;
        proxy_read_timeout 60s;
    }
}
```

```sh
nginx -t
systemctl reload nginx
```

应用启动前 443 出现 502 是上游尚未运行，完成第 9 节后再验收。`AUTH_ALLOWED_ORIGINS` 必须精确配置真实 HTTPS Origin。保留 prod 的 Secure Cookie、CSRF 和来源校验；不要通过关闭这些机制解决部署问题。

默认代理信任范围较窄，不保证获得真实客户端 IP。若需要真实 IP，逐跳核对主机网关 → 前端容器 → 后端的实际连接地址，再配置准确的 `TRUSTED_PROXY_CIDR`、`TRUSTED_PROXY_REGEX` 和转发策略，不照抄整个私网网段。

### 8.4 验证续期和重新加载

```sh
install -d -m 0755 /etc/letsencrypt/renewal-hooks/deploy
cat > /etc/letsencrypt/renewal-hooks/deploy/maple-nginx-reload.sh <<'EOF'
#!/bin/sh
set -eu
/usr/sbin/nginx -t
/bin/systemctl reload nginx
EOF
chmod 0750 /etc/letsencrypt/renewal-hooks/deploy/maple-nginx-reload.sh
/snap/bin/certbot renew --dry-run
systemctl list-timers --all | grep -i certbot
```

保留 80 验证路径，确认自动续期调度、证书到期监控和 reload hook。dry-run 不等于所有未来续期必然成功；云证书方案则使用对应续期和部署方式，不同时维护两套未知流程。

## 9. 首次部署、改密与关闭引导

首次空库、访问仅限维护人员时，把根 `.env` 的 `ADMIN_BOOTSTRAP_ENABLED` 改为 `true`。已有实例保持 false，不把引导当作重置密码功能。

```sh
cd /opt/maple-hush/maple-hush-admin
docker compose config --quiet
docker compose config --services
docker compose up -d --build --wait --wait-timeout 600
docker compose ps
curl -fsS http://127.0.0.1:8090/healthz
curl -fsS http://127.0.0.1:8090/api/auth/captcha
docker compose exec backend curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

应只有 frontend/backend/mysql/redis 四服务，只有 frontend 映射 `127.0.0.1:8090`。MySQL/Redis 健康后后端才启动；frontend healthy 只说明静态服务正常。构建中的 Maven 跳过测试，提交前的 CI/功能验证仍须完成。

打开真实 HTTPS 域名，初始管理员按项目约定为 `admin/12345678`，完成首次改密并重新登录。密码哈希持久化在 MySQL。然后把 `.env` 的 `ADMIN_BOOTSTRAP_ENABLED` 改回 `false`：

```sh
docker compose up -d --wait --wait-timeout 600
```

验证登录、工作台、菜单权限、一次关键写入、会话刷新、退出、OSS 上传；新增用户可不选角色，无角色账号改密后只有基础工作台。prod 不公开 Knife4j、OpenAPI 和后端探针。验收完成后再开放正常业务访问范围。

## 10. 以后每次更新

先在开发机测试、提交并推送；生产选择已验收版本。服务器备份、检查工作区、拉取代码，每条成功后再继续：

```sh
cd /opt/maple-hush/maple-hush-admin
bash deploy/ops/backup-mysql.sh "/opt/maple-hush/backups/maple-hush-$(date +%Y%m%d-%H%M%S).sql"
git status --short
git pull --ff-only
git log -1 --oneline
```

记录旧提交和镜像 digest；把 `.env` 的两个 release 标签改为本次新版本，保留旧镜像与旧配置，再执行：

```sh
docker compose up -d --build --wait --wait-timeout 600
docker compose ps
docker compose logs --tail 100 backend frontend
```

不需要先删容器或 stop；Compose 自动更新需要变化的容器并保留命名卷。`up -d` 不保证重新构建源码；`restart` 不编译代码、不载入新的容器环境。修改源码使用 `--build`，修改 `.env` 用 `up -d` 应用；`--no-build` 用于已构建镜像或经验证的镜像回退。`--wait-timeout` 是服务健康等待时间，不是构建超时。

单侧更新仍在根目录使用同一项目配置：

```sh
docker compose up -d --build --no-deps --wait --wait-timeout 600 frontend
docker compose up -d --build --wait --wait-timeout 600 backend
```

生产备份应复制到异机/独立存储并定期隔离恢复；镜像回退前确认旧应用兼容当前 schema。使用旧镜像回退不能带 `--build` 把旧标签覆盖成新源码；数据库恢复不是应用镜像回退。操作详见[运维手册](../../deploy/ops/README.md)。

## 11. 故障定位与最后核对

```sh
docker compose ps
docker compose logs --tail 100 mysql redis backend frontend
docker buildx ls
journalctl -u docker --since '10 minutes ago' --no-pager -n 120
ss -lntp | grep -E ':(22|80|443|3306|6379|8080|8090)\b'
df -h /
docker system df
```

| 问题 | 定位 |
| --- | --- |
| apt 下载失败 | Ubuntu 代号、传统/DEB822 格式、CA、官方 security 源和当前网络 |
| Docker 安装失败 | Docker CE apt 仓库和签名；不是 registry-mirrors |
| 基础镜像拉取失败 | 企业仓库域名、登录权限、实际镜像/tag/digest、解析器镜像是否也已同步 |
| npm/Maven 下载失败 | 构建容器网络、对应公开源参数、镜像同步延迟；宿主机配置不会自动进入容器 |
| Dockerfile 第一行 gRPC 报错 | BuildKit 解析器与 daemon 日志；frontend 一词不是业务前端，不盲目删卷或重装 |
| MySQL unhealthy | 日志、初始化进度、磁盘、healthcheck 和已持久化密码，不直接删卷 |
| 欢迎页/502 | server_name、站点软链接、nginx -t/reload、回环端口与后端 readiness |
| 登录后页面失败 | 浏览器 Console、后续请求、实际构建版本与 Origin，不直接重置密码 |
| Navicat 连接失败 | 当前默认不发布 3306，需要另行配置受控的回环端口加 SSH 通道 |

停止可用 `docker compose stop`，再次启动用 `docker compose up -d --wait --wait-timeout 600`。不得用 `docker compose down -v` 或清理全部 volume 解决构建/账号故障；系统 Docker 数据目录也不随意删除。

上线记录至少包括：系统/Engine/Compose 版本、Git 提交、应用及基础镜像 digest、Flyway 版本、配置版本、备份校验值、登录/权限/上传/刷新验收、证书与备份负责人。记录不包含密钥。监控磁盘、容器日志、内存、readiness、证书到期和备份失败。本地虚拟机“可以了”不替代这里的生产、TLS 和异机恢复验收。

## 12. 本次文档与构建配置核对（2026-10-27）

- 基于 Docker、Ubuntu、镜像站和 Maven/Certbot 官方文档整理，引用均放在对应步骤旁；安装命令限定为本文声明的 Ubuntu/架构。
- 文档本地文件链接、shell 块语法、内嵌 Python、JSON/XML 解析通过；这只校验语法，不执行系统安装或镜像上传。
- Compose standalone 5.6.0 解析根、backend、frontend 三目录配置，以及本文 prod 官方/企业镜像参数，确认构建源和解析器参数传入，四服务、七个运行密钥和回环端口隔离保留。
- 后端新 settings 实际执行 `mvnw.cmd -s deploy/maven-settings.xml -B -ntp -DskipTests package`，环境变量 `MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2`，reactor BUILD SUCCESS；本次跳过测试，不写成业务测试通过。未声称已在目标网络验证阿里云镜像完整依赖解析。
- 后端 apt 替换 shell 在隔离文本文件中验证：空值保留、HTTPS 镜像替换、HTTP/非法分隔符拒绝、security 源不变。未运行真实 apt 安装。
- 当前编写端没有 Docker Engine；尚未实际执行全新服务器 apt/Docker 安装、Docker 镜像构建/推送、域名证书签发、生产启动和异机恢复。这些需在正式目标环境按步骤执行并补充记录。
