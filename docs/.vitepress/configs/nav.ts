import type { DefaultTheme } from 'vitepress';

export default [
  {
    text: '文档',
    activeMatch: '^/(introduction|getting-started|development|standards|deployment|release|faq)/',
    items: [
      { text: '项目介绍', link: '/introduction/' },
      { text: '快速开始', link: '/getting-started/' },
      { text: '开发指南', link: '/development/' },
      { text: '开发规范', link: '/standards/' },
      { text: '部署运维', link: '/deployment/' },
      { text: '发版与升级', link: '/release/' },
      { text: '常见问题', link: '/faq/' },
    ],
  },
  {
    text: '更新记录',
    link: 'https://github.com/maplehush/maple-hush-admin/commits/main/',
  },
  { text: '问题反馈', link: 'https://github.com/maplehush/maple-hush-admin/issues' },
] satisfies DefaultTheme.NavItem[];

export const englishNav: DefaultTheme.NavItem[] = [
  {
    text: 'Documentation',
    activeMatch: '^/en/(introduction|getting-started|development|standards|deployment|release|faq)/',
    items: [
      { text: 'Introduction', link: '/en/introduction/' },
      { text: 'Getting Started', link: '/en/getting-started/' },
      { text: 'Development Guide', link: '/en/development/' },
      { text: 'Development Standards', link: '/en/standards/' },
      { text: 'Deployment & Operations', link: '/en/deployment/' },
      { text: 'Releases & Upgrades', link: '/en/release/' },
      { text: 'FAQ', link: '/en/faq/' },
    ],
  },
  { text: 'Updates', link: 'https://github.com/maplehush/maple-hush-admin/commits/main/' },
  { text: 'Report an Issue', link: 'https://github.com/maplehush/maple-hush-admin/issues' },
];
