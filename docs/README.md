# Maple Hush Admin 文档工程

独立的 VitePress 文档工程，与 frontend、backend 分开安装和启动。中文位于根路径，英文页面在 `en/` 下按同一路径镜像，顶部语言菜单切换对应文章。目前已完成 D01 项目介绍四页、D02 快速开始五页、D03 开发指南六页和 D04 开发规范六页；D07 常见问题已有指引及三篇分类文章，持续补充；D05/D06 继续保留待完善入口。

## 环境

- Node.js 22 或更高版本；本次验证版本为 22.21.1，`.node-version` 供版本管理工具使用。
- pnpm 12.3.4，与 packageManager 固定版本一致。
- VitePress 1.6.4（稳定版）。

## 本地启动

从仓库根目录执行：

```sh
cd docs
pnpm install --frozen-lockfile
pnpm dev
```

访问 http://127.0.0.1:5174/ 。无需启动前端、后端、MySQL 或 Redis；终端按 Ctrl+C 停止。

开发端口固定为 5174，端口占用时直接报错，不自动跳到别的地址。更换端口可以使用 `pnpm dev --port 5175`。

## 构建与预览

在 docs 目录执行：

```sh
pnpm build
pnpm preview
```

预览地址为 http://127.0.0.1:4174/ ，构建产物位于 `.vitepress/dist/`，缓存、依赖和产物不提交 Git。

## 文件说明

- `.vitepress/config.ts`：中文站点及默认主题配置。
- `.vitepress/configs/nav.ts`：中英文文档菜单、更新记录与问题反馈。
- `.vitepress/configs/sidebar.ts`：中英文七栏目侧栏；仅项目介绍可折叠，其余分组始终展开。
- `.vitepress/theme/`：默认主题扩展、字体、首页间距与技术栈表格样式。
- `index.md`：网站首页与介绍入口。
- `introduction/`：D01 四个页面。
- `getting-started/`：D02 五页使用教程。
- `development/`：D03 公告管理二次开发教程六页。
- `standards/`：D04 总览、后端、前端、接口、数据库与 Git 六页规范。
- `faq/`：排查指引、启动配置、登录权限、构建与代码更新四页。
- `deployment/`、`release/`：待完善入口，尚无子文章。
- `en/`：完整英文页面，与中文页面一一对应；D05/D06 同样保留待完善。
- `assets/images/`：中英文 SVG 架构图。
- `pnpm-workspace.yaml`：仅许可 esbuild 构建脚本，不接入 frontend workspace。
- 本 README 是维护说明，通过 srcExclude 排除，不生成公开页面。

内部栏目规划和实施任务保留在 `开发文档/文档站点/`。当前不配置域名、发布流水线或服务器部署。

## 双语维护

新增或更新公开文章时，同步对应的 `en/` 页面、标题锚点、导航和示例说明。路径、API 字段、权限码、命令等可执行标识保持一致；真实响应中的中文提示不改成虚构的英文响应。示例中的中英文语言资源按用途保留。

站内链接指向当前语言的页面，图示使用对应语言资源。本地搜索按当前语言索引文章，无需额外搜索服务。修改后构建 docs，检查两种语言的链接、搜索及同篇文章切换。
