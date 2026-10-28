# 目录结构

下面按当前仓库列出主要入口，省略依赖目录、构建产物和同类文件。路径都相对于产品仓库根目录，读者无需拥有维护者的工作区或参考代码。

## 仓库根目录

```text
maple-hush-admin/
├── frontend/                Vben 前端 workspace
├── backend/                 Spring Boot 四模块后端
├── docs/                    面向使用者的 VitePress 文档
├── deploy/                  根 Compose 覆盖配置与运维脚本
├── 开发文档/                 内部设计、任务与验收记录
├── .github/workflows/       当前代码检查工作流
├── .env.example             根部署参数示例
├── docker-compose.yml       根整体编排入口
└── README.md                仓库说明
```

`docs/` 是对外阅读入口；`开发文档/` 供研发维护过程使用，不能直接作为整个公开网站发布。`deploy/` 保存已有配置，不代表自动发布流水线已经交付。

## 前端工程

```text
frontend/
├── apps/
│   └── web-antdv-next/       当前目标应用
│       ├── src/
│       │   ├── adapter/     组件、表单和表格适配
│       │   ├── api/         应用请求与接口类型
│       │   ├── auth/        登录会话、刷新与跨标签页协调
│       │   ├── layouts/     布局接入
│       │   ├── locales/     应用国际化词条
│       │   ├── router/      路由、守卫与动态菜单装配
│       │   ├── store/       应用状态
│       │   ├── utils/       应用工具
│       │   └── views/       页面
│       │       ├── _core/   登录、个人中心和回退页
│       │       ├── dashboard/ 工作台等概览页
│       │       ├── log/     登录与操作日志
│       │       └── system/ 用户、角色、菜单、部门、字典
│       ├── package.json
│       └── vite.config.ts
├── packages/                workspace 公共包
├── internal/                构建与代码检查配置
├── scripts/                 工具与前端部署脚本
├── pnpm-workspace.yaml      workspace 范围与 catalog
├── pnpm-lock.yaml           实际依赖版本
└── package.json             前端命令与工具要求
```

公共 `packages/` 与应用代码共同构成前端。新增业务页面通常落在目标应用中；需要复用的底层能力先查公共包，避免重复实现。

## 后端工程

```text
backend/
├── admin-base-core/
│   └── src/main/java/com/maplehush/admin/
│       ├── common/          响应、异常、实体、认证基础、存储等
│       └── config/          Redis、MyBatis、Web 公共配置
├── admin-system-api/
│   └── src/main/java/com/maplehush/admin/system/api/
│                            当前用户、权限和部门等内部契约
├── admin-system-biz/
│   └── src/main/java/com/maplehush/admin/system/
│       ├── auth/            登录、会话、安全链与首次引导
│       ├── user/            用户与个人资料业务
│       ├── role/            角色及授权
│       ├── menu/            菜单维护与路由数据
│       ├── dept/            部门
│       ├── dict/            字典
│       ├── log/             登录和操作日志、审计
│       ├── permission/      权限计算与授权变更
│       ├── file/            文件上传接口
│       └── docs/            文档访问策略
├── admin-system-start/
│   ├── src/main/java/com/maplehush/admin/
│   │   ├── AdminApplication.java
│   │   └── config/docs/     运行时文档装配与启动日志
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   ├── application-test.yml
│   │   ├── application-prod.yml
│   │   └── db/migration/    Flyway 迁移
│   └── src/test/            启动模块测试与集成验证
├── .mvn/wrapper/            Maven Wrapper 配置
├── deploy/                  后端独立部署辅助文件
├── mvnw / mvnw.cmd          Maven 命令入口
├── docker-compose.yml       后端及依赖独立编排
└── pom.xml                  聚合构建与版本管理
```

业务模块按功能分包，典型功能内包含 `controller`、`dto`、`vo`、`service`、`service/impl`、`mapper`、`entity`。不是每个功能都必须创建全部子目录，按实际职责组织。

Service 使用 `I...Service` 接口和 `...ServiceImpl` 实现，调用方注入接口。新增模块的具体操作将在开发指南中补充。

## 常用定位入口

| 想做什么 | 从哪里开始 |
| --- | --- |
| 调整用户页面 | frontend/apps/web-antdv-next/src/views/system/user/ |
| 查找用户接口请求 | frontend/apps/web-antdv-next/src/api/system/user.ts |
| 查看统一请求处理 | frontend/apps/web-antdv-next/src/api/request.ts |
| 查看登录与会话协调 | frontend/apps/web-antdv-next/src/auth/ |
| 查看动态路由 | frontend/apps/web-antdv-next/src/router/access.ts |
| 查找用户后端逻辑 | backend/admin-system-biz/src/main/java/com/maplehush/admin/system/user/ |
| 查看 HTTP 文档装配 | backend/admin-system-start/src/main/java/com/maplehush/admin/config/docs/ |
| 查找数据库迁移 | backend/admin-system-start/src/main/resources/db/migration/ |
| 查看环境配置 | backend/admin-system-start/src/main/resources/application*.yml |
| 查看响应和错误定义 | backend/admin-base-core/src/main/java/com/maplehush/admin/common/ |
| 调整文档导航 | docs/.vitepress/configs/nav.ts 与 sidebar.ts |

## 文档工程

```text
docs/
├── .vitepress/
│   ├── config.ts            站点配置
│   ├── configs/
│   │   ├── nav.ts           顶部导航
│   │   └── sidebar.ts       栏目侧栏
│   └── theme/               默认主题扩展与表格样式
├── assets/images/           中英文文档插图
├── en/                      按相同栏目结构组织的完整英文页面
├── introduction/            本栏目四个页面
├── getting-started/         快速开始五页
├── development/             开发指南六页
├── standards/               开发规范（六个页面）
├── deployment/              部署运维（仅占位首页）
├── release/                 发版与升级（仅占位首页）
├── faq/                     常见问题（指引及三篇分类文章）
├── index.md                 网站首页
├── package.json             独立安装、开发与构建命令
├── pnpm-lock.yaml           文档依赖锁文件
├── pnpm-workspace.yaml      构建脚本许可
└── README.md                工程维护说明，不生成公开页面
```

七个栏目目录均已创建，其中项目介绍有四篇正文、快速开始有五篇正文、开发指南和开发规范各有六篇正文，常见问题有指引及三篇分类文章，部署运维与发版升级保留待完善入口。后续子文章在对应任务中逐步增加，不预先创建空白文章。

中文页面位于根路径，英文页面位于 `/en/`，通过头部语言菜单切换同一篇文章。新增文章时同步维护两种语言及其链接。
