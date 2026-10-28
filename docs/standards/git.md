# Git 协作

一个仓库管理 frontend、backend、docs 与部署配置。日常开发使用 `develop`，`main` 保存经过验收的稳定代码。

## 分支职责

| 分支 | 用途 |
| --- | --- |
| `develop` | 日常功能集成和开发验证 |
| `main` | 验收后的稳定版本与发布来源 |
| 功能分支 | 从 develop 创建，承载一个明确功能或修复 |

功能分支可以命名为 `feat/notice`、`fix/login-error` 或 `docs/api-rules`。功能完成后审查合入 develop；阶段验收后再合入 main。两个长期分支在开发期间可以不同，不为保持一致而跳过验收。

以下命令在**仓库根目录**执行，展示常见的新功能流程；运行前先检查本地改动：

```sh
git status
git switch develop
git pull --ff-only
git switch -c feat/notice
```

## 更新代码与冲突

`git pull --ff-only` 只允许以快进方式更新**当前分支**，通常从该分支配置的 upstream 获取更新。它不代表只拉 main，也不会自动更新本地所有分支。

快进表示本地没有分叉提交，可以把分支指针直接向前移动。已经分叉时命令会停止，给你机会检查本地与远程提交，不会自动生成一次合并。

需要明确来源时，例如当前在 develop：

```sh
git pull --ff-only origin develop
```

出现冲突先阅读双方修改及相关接口、迁移影响，解决后重新检查。不要用强推、整文件覆盖或清理工作区来代替理解冲突；已共享分支的历史变更需要团队协调。

## 提交内容与消息

一次提交表达一个明确目的。业务改动与对应测试、迁移、文档可以一起提交，无关格式化或重构分开处理。

采用 `type: description` 或 `type(scope): description` 形式，例如：

```text
feat(user): support optional role assignment
fix(auth): clarify login failure handling
docs: add development standards
```

常用 type 为 `feat`、`fix`、`docs`、`refactor`、`test`、`chore`；具体允许项由现有 commitlint 配置决定。描述说明改变了什么，必要时在提交正文写原因和兼容影响。

先选择文件、查看暂存差异，再提交。例如只修改一篇文档：

```sh
git add docs/standards/api.md
git diff --cached
git commit -m "docs: clarify API response conventions"
```

避免在不了解工作区内容时直接提交全部文件，尤其是配置、生成产物或他人未完成的修改。

## 不提交的内容

- 真实环境 `.env`、密码、JWT 签名密钥、OSS AccessKey。
- `secrets/`、证书私钥、备份和含敏感值的调试日志。
- `node_modules/`、`dist/`、`target/` 和文档站缓存、构建产物。
- 与项目无关的个人编辑器配置或工作区临时文件。

根 `.gitignore` 保留 `.env.example` 及目标前端应用的部分公开配置文件。被允许跟踪不表示可以放密钥，前端变量必须能公开。

本地 `dev` 的示例数据库、Redis 默认值是工程明确保留的开发约定，不能复制为测试、生产凭据。真实密钥误提交后需要撤销或轮换；删除工作区文件不会抹掉历史记录。

## 当前自动检查

当前 `.github/workflows/ci.yml` 对 main、develop 的 push 和 pull request 运行以下检查：

| 工程 | 当前检查 |
| --- | --- |
| frontend | 锁文件安装、ESLint、Oxfmt、目标应用类型检查、Vitest、目标应用构建 |
| backend | Java 21 环境下执行 Maven Wrapper `verify` |

前端还提供 `lefthook.yml`：提交前包含 Oxlint、Oxfmt、ESLint、Stylelint 和类型检查；commit-msg 使用 commitlint。钩子是否运行取决于本地是否安装并启用，不能认为每台克隆仓库的电脑都已生效。自动修复可能修改暂存文件，提交前查看结果。

**当前 CI 是代码检查。** 它没有交付测试环境自动部署、生产审批发布或镜像晋级，也没有单独的 docs 构建任务。不能据此认定仓库已配置强制分支保护或完整发布流水线。

## 合并前说明

提交或合并请求说明至少包括：

1. 修改目的与主要影响。
2. 接口、数据库、权限、配置是否变化。
3. 实际执行的检查命令与结果，以及尚未验证的部分。
4. 涉及使用方式变化时，对应文档是否同步。

代码检查通过后仍需按变更范围审查行为。文档修改只需检查文档站与内容，不为提交说明额外运行业务服务；业务改动则按前后端规范完成相关验证。
