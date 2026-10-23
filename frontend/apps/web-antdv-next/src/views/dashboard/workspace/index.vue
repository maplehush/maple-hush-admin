<script setup lang="ts">
import type { WorkbenchQuickNavItem } from '@vben/common-ui';
import type { MenuRecordRaw } from '@vben/types';

import { computed } from 'vue';
import { useRouter } from 'vue-router';

import { Page, WorkbenchHeader, WorkbenchQuickNav } from '@vben/common-ui';
import { preferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';

import { $t } from '#/locales';

const router = useRouter();
const accessStore = useAccessStore();
const userStore = useUserStore();

const shortcutIcons: Record<string, string> = {
  '/system/user/list': 'lucide:user-round',
  '/system/role/list': 'lucide:users-round',
  '/system/menu/list': 'lucide:menu',
  '/system/dept/list': 'lucide:building-2',
  '/system/dict/list': 'lucide:book-open',
  '/system/log/login-log/list': 'lucide:log-in',
  '/system/log/operation-log/list': 'lucide:scroll-text',
  '/system/login-log/list': 'lucide:log-in',
  '/system/operation-log/list': 'lucide:scroll-text',
};

const shortcutColors = [
  '#1677ff',
  '#13c2c2',
  '#722ed1',
  '#52c41a',
  '#fa8c16',
  '#eb2f96',
  '#2f54eb',
];

function getSystemPages(menus: MenuRecordRaw[]): MenuRecordRaw[] {
  return menus.flatMap((menu) => {
    if (menu.children?.length) {
      return getSystemPages(menu.children);
    }
    return menu.path.startsWith('/system/') ? [menu] : [];
  });
}

const quickNavItems = computed<WorkbenchQuickNavItem[]>(() =>
  getSystemPages(accessStore.accessMenus).map((menu, index) => ({
    color: shortcutColors[index % shortcutColors.length],
    icon: menu.icon ?? shortcutIcons[menu.path] ?? 'lucide:arrow-up-right',
    title: $t(menu.name),
    url: menu.path,
  })),
);

function handleQuickNav(item: WorkbenchQuickNavItem) {
  if (item.url) {
    void router.push(item.url);
  }
}
</script>

<template>
  <Page :title="$t('page.dashboard.workspace')" content-class="p-0">
    <div class="p-5">
      <WorkbenchHeader
        :avatar="userStore.userInfo?.avatar ?? preferences.app.defaultAvatar"
      >
        <template #title>
          {{
            $t('page.dashboard.welcome', {
              name:
                userStore.userInfo?.realName ||
                userStore.userInfo?.username ||
                '',
            })
          }}
        </template>
        <template #description>
          {{ $t('page.dashboard.description') }}
        </template>
        <template #actions>
          <span class="hidden" aria-hidden="true"></span>
        </template>
      </WorkbenchHeader>

      <WorkbenchQuickNav
        v-if="quickNavItems.length > 0"
        :items="quickNavItems"
        class="mt-5"
        :title="$t('page.dashboard.quickNavigation')"
        @click="handleQuickNav"
      />
    </div>
  </Page>
</template>
