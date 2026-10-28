# 本地配置

本章为后端源码启动准备参数。默认使用本机 MySQL 3306、Redis 6379，前端 5999、后端 8080；实际地址和凭据以你的开发环境为准。

## 先创建开发数据库

用具备创建数据库权限的账号连接 MySQL，在 MySQL 客户端内执行：

```sql
CREATE DATABASE maple_hush_admin
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

仅在这个库尚不存在时执行。已有同名库时先确认用途，可改用另一个新库名并同步 `MYSQL_DATABASE`，不要删除或清空旧库。

后端运行账号必须能在该开发库执行 Flyway 所需的建表、索引及数据读写。可以使用已有专用账号；下文 `root` 仅作为本机开发示例，正式环境使用独立受限账号。

## 后端配置如何加载

配置文件位于 `backend/admin-system-start/src/main/resources/`：

| 文件 | 用途 |
| --- | --- |
| application.yml | 公共配置、认证、日志和 OSS 参数 |
| application-dev.yml | 本地开发连接及允许的前端 Origin |
| application-test.yml | 测试环境 |
| application-prod.yml | 生产环境 |

本章通过启动终端的环境变量激活 `dev` 并覆盖具体参数。

**在仓库根或 backend 中创建 `.env`，不会让 `java -jar` 自动读取它。** 根 `.env` / 后端 `.env.example` 主要服务于 Compose；前端则由 Vite 读取目标应用的 `.env*` 文件，两者不要混用。

## 设置后端参数

执行位置：准备启动后端的终端，当前目录为 `backend/`。先替换示例中的数据库、Redis 密码，再执行。变量只作用于当前终端及其子进程，不写入源码。

::: code-group

```powershell [PowerShell]
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:MYSQL_HOST = '127.0.0.1'
$env:MYSQL_PORT = '3306'
$env:MYSQL_DATABASE = 'maple_hush_admin'
$env:MYSQL_USERNAME = 'root'
$env:MYSQL_PASSWORD = '填写实际的 MySQL 密码'
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '6379'
$env:REDIS_DATABASE = '0'
$env:REDIS_PASSWORD = '填写实际的 Redis 密码'
$env:REDIS_KEY_PREFIX = 'maple_hush_dev'
$env:SERVER_PORT = '8080'
$env:AUTH_ALLOWED_ORIGINS = 'http://localhost:5999,http://127.0.0.1:5999'
$env:ADMIN_BOOTSTRAP_ENABLED = 'true'
$env:ADMIN_BOOTSTRAP_USERNAME = 'admin'
$env:AUTH_JWT_SECRET = node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))"
```

```sh [Bash]
export SPRING_PROFILES_ACTIVE=dev
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_DATABASE=maple_hush_admin
export MYSQL_USERNAME=root
export MYSQL_PASSWORD='填写实际的 MySQL 密码'
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export REDIS_DATABASE=0
export REDIS_PASSWORD='填写实际的 Redis 密码'
export REDIS_KEY_PREFIX=maple_hush_dev
export SERVER_PORT=8080
export AUTH_ALLOWED_ORIGINS='http://localhost:5999,http://127.0.0.1:5999'
export ADMIN_BOOTSTRAP_ENABLED=true
export ADMIN_BOOTSTRAP_USERNAME=admin
export AUTH_JWT_SECRET="$(node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))")"
```

:::

请勿原样保留“填写实际的……”字符串。当前 dev 配置的数据库默认账号/密码是 `root/123456`，Redis 默认密码是 `123456`；这些只是项目的本地默认值，不会自动修改你已安装服务的账号密码。

JWT 示例生成 32 字节随机数并转为十六进制文本，不需要复制输出。`dev` 未配置 JWT 时也会使用进程内随机密钥；重启后旧 access token 可能失效，需要重新登录。固定使用自己的受控密钥时，至少满足 32 字节要求，不提交到 Git。

### Redis 连接

`REDIS_DATABASE` 选择 Redis 的逻辑库，`REDIS_KEY_PREFIX` 区分业务键；同一服务存在多个开发实例时应分别设置，前缀只用字母、数字、下划线或连字符。

若开发 Redis 没有密码，启动 Jar 时显式附加：

```sh
java -jar admin-system-start/target/admin-system-start-0.0.1-SNAPSHOT.jar --spring.data.redis.password=
```

这会覆盖开发配置中的默认密码。尤其在部分 PowerShell 环境中，设置空环境变量可能等同于删除变量，不能仅靠 `$env:REDIS_PASSWORD = ''` 判断最终为空。

## Flyway 与首次管理员

两项初始化职责不同：

| 内容 | 谁负责 |
| --- | --- |
| 创建数据库本身 | 你提前在 MySQL 创建 |
| 创建业务表、初始化角色菜单、后续表结构迁移 | 应用启动时 Flyway 自动执行 |
| 创建首个超级管理员 | 空库且启用 ADMIN_BOOTSTRAP_ENABLED 时由应用引导 |

不要手动导入内部 SQL 草案，也不要重复导入 Flyway 文件。已执行过的迁移不可随意修改，历史记录保存在 `flyway_schema_history`。

首次空库使用 `ADMIN_BOOTSTRAP_ENABLED=true`，默认创建 `admin`，临时密码为 **12345678**，必须通过首次改密页面设置新密码。若改了 `ADMIN_BOOTSTRAP_USERNAME`，登录时使用实际账号。

已有正常管理员的库不需要重新引导，设置为 `false`；重新开启引导、修改用户名参数或重建应用都不会重置数据库里已有账号的密码。

## OSS 可稍后配置

基础启动阶段可以不设置 OSS 凭据，先完成登录、权限和管理页面联调。未配置时，头像和文件上传返回依赖不可用，不应据此判断整个应用无法启动。

后续需要上传时，在后端运行环境设置实际参数：

| 参数 | 用途 |
| --- | --- |
| OSS_REGION | Bucket 所在地域 |
| OSS_BUCKET | 实际 Bucket 名称 |
| OSS_PUBLIC_DOMAIN | 与实际对象访问方式匹配的 HTTPS 域名 |
| OSS_OBJECT_PREFIX | 业务对象前缀 |
| OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET | 后端凭据 |
| OSS_SECURITY_TOKEN | 使用临时凭据时的令牌 |

不要将这些凭据放入前端 `VITE_` 变量或提交进仓库。

## IDE 启动方式

如果使用 IDE，导入 `backend/pom.xml`，选择 JDK 21；运行类是 `com.maplehush.admin.AdminApplication`，所在模块为 `admin-system-start`。

终端设置的变量不一定会进入已经打开的 IDE。使用 IDE 启动时，将上述参数配置到对应运行配置中，并激活 dev；不要同时启动 IDE 和 Jar 占用同一端口。

配置完成后进入[启动与首次登录](./first-run.md)。
