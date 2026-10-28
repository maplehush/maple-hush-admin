# 下载与安装

## 下载完整仓库

执行位置：你准备存放项目的父目录。

```sh
git clone --branch main --single-branch https://github.com/maplehush/maple-hush-admin.git
cd maple-hush-admin
git branch --show-current
git status --short
```

预期：当前分支为 `main`，工作区没有本地改动。`main` 是阅读和使用的起点；参与开发时按团队协作流程使用 `develop` 或对应功能分支。

不要只下载 `frontend/apps/web-antdv-next/`：它依赖前端 workspace 中的公共包和构建配置。仓库根目录应能看到 `frontend/`、`backend/`、`docs/` 等目录。

如果目录已存在，先确认是否就是你要使用的仓库，不重复克隆覆盖，也不要为了教程清除已有改动。

## 安装前端依赖

执行位置：仓库的 `frontend/`，不是 `docs/` 或目标应用子目录。

```sh
cd frontend
node --version
pnpm --version
pnpm install --frozen-lockfile
```

预期：Node 符合工程范围，pnpm 为 **11.16.0**，安装完成并退出为 0。首次安装还会执行 workspace 的准备脚本，时间取决于网络和机器性能。

`--frozen-lockfile` 按仓库已有锁文件安装，避免使用者安装时重新解析出另一套版本。安装失败时保留错误输出，先区分版本不符、网络下载失败和依赖脚本问题，不通过删除锁文件重试。

本工程直接使用 `pnpm` 命令。不把 `corepack pnpm install` 作为替代入口，避免子脚本落到与工程不同的全局 pnpm。

## 检查 Maven Wrapper

另开终端，进入仓库的 `backend/`。

::: code-group

```powershell [PowerShell]
.\mvnw.cmd --version
```

```sh [Bash]
sh ./mvnw --version
```

:::

预期：Maven **3.9.9**，Java **21**。POSIX 示例显式使用 `sh`，因此不要求检出后的 `mvnw` 已带可执行权限。

首次运行会下载 Wrapper 指定的 Maven 发行包。不要为了使用项目而修改 `distributionUrl` 或升级父 POM；下载失败时先检查网络、代理及 JDK。

## 准备后端构建

仍在 `backend/` 执行：

::: code-group

```powershell [PowerShell]
.\mvnw.cmd -DskipTests package
```

```sh [Bash]
sh ./mvnw -DskipTests package
```

:::

预期：输出 `BUILD SUCCESS`，生成：

```text
backend/admin-system-start/target/admin-system-start-0.0.1-SNAPSHOT.jar
```

此命令聚合构建四个模块。`-DskipTests` 用于这条本地快速启动路线，不代表项目测试已经通过；正式开发的检查要求在后续开发规范中说明。

不要运行 `*.jar.original`，也不要启动 core、api 或 biz 模块的 Jar；唯一应用入口在 `admin-system-start`。

## 下一步

先完成[本地配置](./configuration.md)，再启动应用。只有打包成功还不能说明 MySQL、Redis 和管理员已经准备好。
