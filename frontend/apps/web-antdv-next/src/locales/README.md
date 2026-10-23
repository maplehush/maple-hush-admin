# 应用语言资源

目标应用支持简体中文（`zh-CN`）和英文（`en-US`）。应用文案放在 `langs/{locale}` 下，`page.json` 存放页面与认证/个人中心文案，`system.json` 存放系统管理模块文案，`demos.json` 存放演示页面标题。两个受支持语言的 JSON 键结构必须一致，`messages.test.ts` 会检查结构。

`index.ts` 负责将当前语言应用到 Vben、AntdvNext 和 Day.js。无效或历史遗留语言偏好回退到 `zh-CN`。添加产品文案时同步维护两种语言；可由用户填写的业务数据不作为静态语言资源。
