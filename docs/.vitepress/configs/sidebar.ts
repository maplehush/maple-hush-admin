import type { DefaultTheme } from 'vitepress';

const sections: DefaultTheme.SidebarItem[] = [
  {
    text: '项目介绍',
    collapsed: false,
    items: [
      { text: '项目简介', link: '/introduction/' },
      { text: '技术栈', link: '/introduction/technology' },
      { text: '系统架构', link: '/introduction/architecture' },
      { text: '目录结构', link: '/introduction/directory' },
    ],
  },
  {
    text: '快速开始',
    items: [
      { text: '开始前须知', link: '/getting-started/' },
      { text: '环境准备', link: '/getting-started/prerequisites' },
      { text: '下载与安装', link: '/getting-started/installation' },
      { text: '本地配置', link: '/getting-started/configuration' },
      { text: '启动与首次登录', link: '/getting-started/first-run' },
    ],
  },
  {
    text: '开发指南',
    items: [
      { text: '开发流程与示例约定', link: '/development/' },
      { text: '新增业务模块', link: '/development/new-module' },
      { text: '数据库与接口', link: '/development/database-api' },
      { text: '新增前端页面', link: '/development/new-page' },
      { text: '菜单与权限', link: '/development/permissions' },
      { text: '接口联调', link: '/development/api-integration' },
    ],
  },
  {
    text: '开发规范',
    items: [
      { text: '规范总览', link: '/standards/' },
      { text: '后端规范', link: '/standards/backend' },
      { text: '前端规范', link: '/standards/frontend' },
      { text: '接口规范', link: '/standards/api' },
      { text: '数据库规范', link: '/standards/database' },
      { text: 'Git 协作', link: '/standards/git' },
    ],
  },
  { text: '部署运维', link: '/deployment/' },
  { text: '发版与升级', link: '/release/' },
  {
    text: '常见问题',
    items: [
      { text: '排查指引', link: '/faq/' },
      { text: '启动与配置', link: '/faq/startup' },
      { text: '登录与权限', link: '/faq/authentication' },
      { text: '构建与代码更新', link: '/faq/build-deployment' },
    ],
  },
];

// 仅项目介绍可折叠；其他栏目不设置 collapsed，始终展开。
export default sections satisfies DefaultTheme.Sidebar;

const englishLabels: Record<string, string> = {
  '项目介绍': 'Introduction',
  '项目简介': 'About the Project',
  '技术栈': 'Technology Stack',
  '系统架构': 'System Architecture',
  '目录结构': 'Directory Structure',
  '快速开始': 'Getting Started',
  '开始前须知': 'Before You Start',
  '环境准备': 'Prerequisites',
  '下载与安装': 'Download & Install',
  '本地配置': 'Local Configuration',
  '启动与首次登录': 'Startup & First Login',
  '开发指南': 'Development Guide',
  '开发流程与示例约定': 'Workflow & Example Conventions',
  '新增业务模块': 'Add a Business Module',
  '数据库与接口': 'Database & APIs',
  '新增前端页面': 'Add a Frontend Page',
  '菜单与权限': 'Menus & Permissions',
  '接口联调': 'API Integration',
  '开发规范': 'Development Standards',
  '规范总览': 'Overview',
  '后端规范': 'Backend Standards',
  '前端规范': 'Frontend Standards',
  '接口规范': 'API Standards',
  '数据库规范': 'Database Standards',
  'Git 协作': 'Git Workflow',
  '部署运维': 'Deployment & Operations',
  '发版与升级': 'Releases & Upgrades',
  '常见问题': 'FAQ',
  '排查指引': 'Troubleshooting Guide',
  '启动与配置': 'Startup & Configuration',
  '登录与权限': 'Login & Permissions',
  '构建与代码更新': 'Builds & Code Updates',
};

function translateItem(item: DefaultTheme.SidebarItem): DefaultTheme.SidebarItem {
  return {
    ...item,
    text: item.text ? englishLabels[item.text] : undefined,
    link: item.link ? `/en${item.link}` : undefined,
    items: item.items?.map(translateItem),
  };
}

export const englishSidebar: DefaultTheme.Sidebar = sections.map(translateItem);
