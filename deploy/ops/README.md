# 上线与运维交接

本页交付 T21 操作步骤，实际通过项以 [T21 记录](../../开发文档/任务拆解/T21-上线演练与运维交接.md)为准。当前无服务器、无本机 Linux 容器引擎；原生 Windows 的隔离演练不等于四服务容器或正式上线验收。

## 1. 部署前与首次启动

按 [统一部署说明](../README.md)准备 Linux Docker、Compose 2.24.4+、受控密钥目录和环境文件。Windows 本地容器验收需先按 [Docker 官方说明](https://docs.docker.com/desktop/setup/install/windows-install/)准备 Docker Desktop/WSL 2，由用户在自己的管理员会话处理系统功能及必要重启；本轮没有更改系统功能或安装系统服务。

预发布使用独立 test 环境，正式发布使用 prod；数据库、Redis 前缀、JWT、OSS Bucket/前缀及证书各自隔离。第一次空库明确开启管理员引导，完成初始密码改密后关闭。根模式只有前端入口对外提供 HTTPS；精确配置前端公开 Origin 和逐跳可信代理。先核对 `docker compose config`，再启动并验证 backend readiness 与 `/api`，不能只看 frontend healthz。

每次发布记录：Git 提交、前后端镜像标签和不可变 digest、Flyway 成功版本、环境文件版本、密钥存储位置/负责人、数据库备份校验值。记录不得包含密码、token、验证码答案或 OSS Key。镜像检查及预发布完整验证未完成时，不标记可发布。

## 2. MySQL 备份

在仓库根、正确的 Compose 项目环境中执行：

```sh
bash deploy/ops/backup-mysql.sh /受控备份目录/maple-hush-20261019.sql
```

脚本输出 SQL、`.sha256`、`.metadata` 和 `.images`。使用 InnoDB 单事务备份、包含触发器/事件/存储过程，记录 Git 提交、Flyway 版本及运行镜像信息；密码从容器 root secret 文件读取，不放命令参数。不覆盖已有文件，失败不报告成功。备份期间禁止同时进行 DDL/迁移；数据库账号变化时先核对备份账号权限。

`COMPOSE_PROJECT_NAME`、`COMPOSE_ENV_FILES` 可按部署环境设置；脚本从仓库根运行 Compose，需与运行服务使用相同项目/环境，不能误用另一套 `.env`。生产 SQL 含用户密码哈希及会话哈希等敏感数据，应存受控目录、加密传输并复制到另一台机器/独立存储。JWT、数据库/Redis 密码、OSS 凭据、TLS 私钥需通过独立受控密钥备份恢复；SQL 不负责恢复这些文件。

在服务器准备好后，通过该部署账号的系统调度器每天执行备份并检查退出码，建议保留每日备份至少 30 天；定期做恢复验证、容量检查和异地副本核对。**本轮没有安装/启用定时任务，也没有完成异机恢复。** 不自动删除任何现有备份。

## 3. 校验与隔离恢复

将 SQL、校验文件和发布记录复制到目标恢复机器的受控目录，准备独立 MySQL Compose 项目后执行：

```sh
bash deploy/ops/restore-mysql.sh /受控备份目录/maple-hush-20261019.sql maple_hush_restore_drill
```

脚本校验实际 SQL 的 SHA-256，只接受 `maple_hush_restore_` 前缀的目标库名，使用 `CREATE DATABASE` 新建目标库；拒绝恢复到当前业务库，不删除或清空已有数据库。输入备份必须来自本备份脚本，不能含 `CREATE DATABASE`/`USE` 重新选库指令；不得执行来源不明的 SQL。恢复失败时保留新建的部分恢复库供检查，不能当作成功库切换流量。

核对所有业务表/行数、关键记录、Flyway 历史和约束，再为新库提供合适的应用用户权限，在隔离配置中启动 Jar。先确认数据正确，再安排停写、切换和回退点。不要让正式应用与恢复演练共用 Redis 前缀或 OSS 写入范围。

**恢复数据库快照可能重新出现旧会话。** 正式灾难恢复时应轮换 JWT 密钥、撤销恢复库内所有 refresh 会话、递增用户 authVersion、清理对应 Redis 快照，要求用户重新登录；操作前停业务写入并备份，先在恢复副本演练。不要在这里给出的通用恢复脚本中自动修改业务数据或密钥。

## 4. 升级与回滚

升级前保存当前配置、镜像 digest、数据库备份和兼容性结论；在与生产同版本的数据副本演练迁移，并确认新旧应用是否同时兼容 schema。示例：

```sh
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
docker compose exec backend curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

上述命令仅为操作入口，不能替代 HTTPS 登录/菜单/关键写入/refresh/退出验证。单侧更新 backend 后，前端 Docker DNS 缓存到期可重新解析新 IP；换 IP 窗口可能出现短暂 API 错误，需核对恢复。

**应用回退**：确认旧应用兼容当前 schema 后，将镜像标签改回上次验收版本，用 `docker compose up -d --no-build --pull never --wait` 重建；确保目标旧镜像确实存在，保留其 digest。不要用 `--build` 把旧标签重新指向新源码。不要把仅修改镜像标签当作数据库回滚。

**数据库恢复**：停写并保留当前失败现场，先恢复到独立库、核对备份时间点与数据损失范围，再决定切换；不要直接运行 Flyway clean 或删除成功迁移记录。删除列/改类型等破坏性变更须先设计兼容发布，不能假定旧镜像可运行。当前只有一套已构建 Jar，没有两版已验收容器镜像；本轮不声称应用镜像回退或生产数据副本升级通过。

## 5. 唯一超级管理员离线恢复

仅由有数据库/主机维护权限的人员核对原账号与备份后执行；不新增公开恢复接口。先停止正常 backend 写入，在受控目录为 UID/GID 10001 准备只限该用户读取的输出目录，再用同一后端镜像以非 Web 模式运行：

```sh
docker compose stop backend
docker compose run --rm --no-deps \
  --volume /受控恢复目录:/recovery \
  backend \
  --spring.main.web-application-type=none \
  --spring.autoconfigure.exclude=com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration,com.github.xiaoymin.knife4j.spring.configuration.insight.Knife4jInsightAutoConfiguration \
  --springdoc.api-docs.enabled=false \
  --springdoc.swagger-ui.enabled=false \
  --knife4j.enable=false \
  --maple-hush.maintenance.recover-super-admin=true \
  --maple-hush.maintenance.recovery-output=/recovery/one-time-password
```

现有维护模式没有自动退出功能；确认恢复文件和数据库审计记录已生成后，终止本次维护进程，再常规启动 backend。恢复输出文件必须不存在，避免覆盖。它只恢复原唯一账号，启用账号、生成随机临时密码、强制改密、递增授权版本并撤销旧 refresh，会写入操作审计。

通过安全渠道将临时密码交给本人，完成强制改密后删除该输出文件并记录处理人/时间；不要在终端日志、工单或聊天中粘贴密码。恢复过程不输出明文到应用日志。不要把重新打开首次引导当作恢复方式，也不要直接创建第二个超级管理员。

## 6. 日常健康、日志与故障

```sh
docker compose ps
docker compose logs --tail 100 backend
docker compose logs --tail 100 frontend
docker compose exec backend curl -fsS http://127.0.0.1:8080/actuator/health/readiness
docker compose exec backend curl -fsS http://127.0.0.1:8080/actuator/health/liveness
```

readiness 包含 MySQL/Redis；liveness 只代表进程。Redis 故障时验证码/登录按依赖错误处理，不能绕过限流；MySQL 继续保存 refresh 会话。Redis 未持久化，重启后旧验证码消失，快照可回源。只清理本项目的键或在独立演练 Redis 使用 FLUSHDB，不能在共享 Redis 直接清库。单纯 unhealthy 不触发 unless-stopped 重启，网关需独立处理健康流量。

会话清理默认保留 7 天、每小时运行；登录日志默认 90 天，操作日志 180 天，每小时分批清理，可通过后端环境配置调整。记录清理失败日志并告警，不能把 Redis 当业务审计备份。日志排查用 Trace ID、时间、状态和固定摘要，不记录密码或令牌。

生产证书由网关负责申请和续期；部署后先实测续期命令/挑战路径、证书链/到期时间，校验 `nginx -t` 后 reload，并设置到期告警。本机演练使用临时自签证书；没有修改 Windows 信任库，不能据此声称正式证书/公网域名续期通过。

上线前从另一台机器核对公开入口、MySQL/Redis/明文后端端口隔离、TLS 与代理头。`docker compose down` 不加 `-v`；不随意清理 Docker volume。只有实际容器、异机恢复、镜像回退及公网验收补齐后，才能更新最终发布状态。
