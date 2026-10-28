# 环境准备

先准备以下工具和服务，再下载和启动项目。版本要求来自仓库配置，不必在宿主机额外安装完整的前端框架或 Spring Boot。

## 工具与版本

| 工具或服务 | 工程要求 / 采用版本 | 用途 |
| --- | --- | --- |
| Git | 可正常克隆 GitHub 仓库的版本 | 获取和更新代码 |
| JDK | **21** | 后端编译和运行；需要完整 JDK，只有 JRE 不够 |
| Node.js | **^22.18.0 或 ^24.12.0** | 前端工具链；前端 .node-version / CI 使用 24.16.0 |
| pnpm | **11.16.0** | frontend/package.json 的 packageManager 固定版本 |
| Maven | Wrapper 固定为 **3.9.9** | 使用仓库脚本，无需再全局安装 Maven |
| MySQL | 项目部署目标 **8.4 LTS** | 数据库及 Flyway 迁移 |
| Redis | 项目部署目标 **7.4** | 验证码、限流和缓存 |

Node.js 的 `^22.18.0` 表示 22.x 且不低于 22.18.0，`^24.12.0` 表示 24.x 且不低于 24.12.0。不要因为其他 Node 主版本更新就直接使用不在工程范围内的版本。

数据库服务端版本与 JDBC / Redis 客户端依赖版本不是同一件事，完整依赖版本见[技术栈](/introduction/technology)。

## 安装工具

- JDK 21：安装适合当前系统的 JDK，设置 `JAVA_HOME`，将其 `bin` 加入 PATH。
- Node.js：通过[官方下载入口](https://nodejs.org/en/download)或已有版本管理工具安装符合上述范围的版本。
- Git：安装后确认终端能使用 `git`。
- pnpm：已有符合要求的 Node/npm 后，可以安装固定版本：

```sh
npm install --global pnpm@11.16.0
```

如果机器已有 pnpm，先进入下载后的 `frontend/` 检查实际版本；项目的 `packageManager` 可能使 pnpm 自动切换到固定版本。不要只看仓库外的全局版本。[pnpm 官方安装说明](https://pnpm.io/installation)

`docs/` 是独立文档工程，固定的 pnpm 版本与前端不同；本章启动的是 `frontend/`。

## 检查工具

执行位置：任意终端。

```sh
git --version
java -version
javac -version
node --version
npm --version
```

预期：Java 与 javac 均为 21，Node 在工程允许范围内。安装或切换版本后，重新打开终端再检查；IDE 的 Project SDK 和运行 JDK 也要指向 21。

## 准备 MySQL 和 Redis

使用本机服务或已有的专用开发实例，记录以下信息：

| MySQL | Redis |
| --- | --- |
| 地址、端口、库名 | 地址、端口、数据库编号 |
| 用户名、密码 | 密码 |
| 对该开发库执行 DDL / DML 的权限 | 独立的业务键前缀 |

MySQL 客户端连接示例，执行后交互输入密码：

```sh
mysql --host=127.0.0.1 --port=3306 --user=root --password
```

成功后可执行 `SELECT VERSION();`，确认连接到了预期实例。

Redis 7 客户端连接示例：

```sh
redis-cli -h 127.0.0.1 -p 6379 --askpass PING
```

预期返回 `PONG`。未设置密码的独立开发 Redis 可去掉 `--askpass`；后端的无密码配置方式见[本地配置](./configuration.md#redis-连接)。旧版客户端可能没有该选项，应按实际客户端使用方式连接。

Redis 的可用安装方式因系统而异，参照[官方安装说明](https://redis.io/docs/latest/operate/oss_and_stack/install/)准备。Windows 不要把历史非官方 Redis 3.x 安装包当成项目的推荐版本；可使用受支持的方式或已有开发实例。本章不展开容器安装与服务器部署。

## 网络准备

首次安装需要访问 GitHub、npm 包源和 Maven 仓库。前端仓库的 `.npmrc` 默认配置 npm 镜像源；Maven Wrapper 还需要下载 Maven 发行包。

宿主机网络、IDE 和不同工具的代理配置可能不同。下载失败时先核对具体仓库、网络和代理，不删除锁文件，也不关闭 TLS 校验。

准备完成后进入[下载与安装](./installation.md)。
