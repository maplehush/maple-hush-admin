import { defineConfig } from 'vitepress';
import nav, { englishNav } from './configs/nav';
import sidebar, { englishSidebar } from './configs/sidebar';

export default defineConfig({
  lang: 'zh-CN',
  title: 'Maple Hush Admin',
  description: 'Maple Hush Admin 项目文档',
  srcExclude: ['README.md'],
  locales: {
    root: { label: '简体中文', lang: 'zh-CN' },
    en: {
      label: 'English',
      lang: 'en',
      description: 'Maple Hush Admin Documentation',
      themeConfig: {
        nav: englishNav,
        sidebar: englishSidebar,
        outline: { label: 'On this page' },
        langMenuLabel: 'Change language',
        darkModeSwitchLabel: 'Appearance',
        lightModeSwitchTitle: 'Switch to light theme',
        darkModeSwitchTitle: 'Switch to dark theme',
        sidebarMenuLabel: 'Menu',
        returnToTopLabel: 'Back to top',
        docFooter: { prev: 'Previous page', next: 'Next page' },
        search: {
          provider: 'local',
          options: {
            translations: {
              button: { buttonText: 'Search docs', buttonAriaLabel: 'Search docs' },
              modal: {
                noResultsText: 'No results found',
                resetButtonTitle: 'Clear search',
                displayDetails: 'Display details',
                backButtonTitle: 'Back',
                footer: { selectText: 'Select', navigateText: 'Navigate', closeText: 'Close' },
              },
            },
          },
        },
      },
    },
  },
  themeConfig: {
    nav,
    sidebar,
    i18nRouting: true,
    search: {
      provider: 'local',
      options: {
        translations: {
          button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
          modal: {
            noResultsText: '未找到相关结果',
            resetButtonTitle: '清空搜索',
            displayDetails: '显示详细内容',
            backButtonTitle: '返回',
            footer: {
              selectText: '选择',
              navigateText: '切换',
              closeText: '关闭',
            },
          },
        },
      },
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/maplehush/maple-hush-admin' },
    ],
    outline: { label: '页面导航' },
    langMenuLabel: '切换语言',
    docFooter: { prev: '上一篇', next: '下一篇' },
    darkModeSwitchLabel: '主题',
    lightModeSwitchTitle: '切换到浅色模式',
    darkModeSwitchTitle: '切换到深色模式',
    sidebarMenuLabel: '菜单',
    returnToTopLabel: '回到顶部',
  },
});
