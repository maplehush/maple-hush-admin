# Maple Hush Admin 前端

基于 Vue Vben Admin 的企业内部后台前端，使用 Vue 3、TypeScript、Vite 和 AntdvNext，支持主题切换、动态菜单和按钮权限。

已接入平台后端，提供登录、个人资料、用户、角色、菜单、部门、字典及日志管理功能。

## 环境要求

- Node.js：`^22.18.0 || ^24.12.0`
- pnpm：`11.16.0`，以本目录 `package.json` 的 `packageManager` 为准。

## 获取代码

```bash
git clone --branch develop https://github.com/maplehush/maple-hush-admin.git
cd maple-hush-admin/frontend
```

日常开发使用 `develop` 分支，`main` 为主分支。

## 安装与启动

在 `frontend/` 目录执行：

```bash
pnpm install --frozen-lockfile
pnpm dev:antdv-next
```

默认访问地址为 `http://localhost:5999`。本地联调需启动后端；前端通过 `/api` 请求真实接口，Mock 已关闭。

## 类型检查与构建

```bash
pnpm --filter @vben/web-antdv-next typecheck
pnpm --filter @vben/web-antdv-next build
```

## 预览

- [Vben Admin](https://vben.pro/)：Vben 官方演示站

以下为 Vben 官方项目预览图：

<div align="center">
  <img alt="Vben Admin 预览图一" width="100%" src="https://anncwb.github.io/anncwb/images/preview1.png">
  <img alt="Vben Admin 预览图二" width="100%" src="https://anncwb.github.io/anncwb/images/preview2.png">
  <img alt="Vben Admin 预览图三" width="100%" src="https://anncwb.github.io/anncwb/images/preview3.png">
</div>

## 来源与许可

前端基于 [Vue Vben Admin](https://github.com/vbenjs/vue-vben-admin)，迁入版本为提交 `151d80f9e5fb63f2ed8a8c432e1adf2eba253271`，组件使用方式可参考 [Vben 官方文档](https://doc.vben.pro/)。

原项目采用 MIT 许可证，保留原有 [LICENSE](./LICENSE) 及版权声明。

## 独立容器部署

前端独立 Compose 入口、TLS 网关示例和参数说明见 [部署说明](./scripts/deploy/README.md)。仅启动 frontend，需要可访问的已部署后端；浏览器仍请求同源 /api。T19 本地验证通过，Docker 容器与公网 HTTPS 完整认证验收待补，具体记录见 [T19 任务卡](../开发文档/任务拆解/T19-前端镜像与独立部署.md)。
