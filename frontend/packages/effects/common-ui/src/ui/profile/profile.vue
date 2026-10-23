<script setup lang="ts">
import type { Props } from './types';

import { preferences } from '@vben-core/preferences';
import {
  Card,
  Separator,
  Tabs,
  TabsList,
  TabsTrigger,
  VbenAvatar,
} from '@vben-core/shadcn-ui';

import { Page } from '../../components';

defineOptions({
  name: 'ProfileUI',
});

withDefaults(defineProps<Props>(), {
  title: '关于项目',
  tabs: () => [],
});

const tabsValue = defineModel<string | undefined>('modelValue', {
  default: undefined,
});
</script>
<template>
  <Page auto-content-height>
    <div class="flex size-full min-w-0 flex-col gap-4 md:flex-row md:gap-0">
      <Card class="w-full min-w-0 flex-none md:w-1/6">
        <div
          class="mt-4 flex h-auto flex-row items-center justify-start gap-3 px-4 md:h-40 md:flex-col md:justify-center md:gap-4 md:px-0"
        >
          <VbenAvatar
            :src="userInfo?.avatar ?? preferences.app.defaultAvatar"
            class="size-14 shrink-0 md:size-20"
          />
          <span class="min-w-0 break-all text-base font-semibold md:text-lg">
            {{ userInfo?.realName ?? '' }}
          </span>
          <span class="ml-auto shrink-0 text-sm text-foreground/80 md:ml-0">
            {{ userInfo?.username ?? '' }}
          </span>
        </div>
        <Separator class="my-3 md:my-4" />
        <Tabs v-model="tabsValue" orientation="vertical" class="m-4">
          <TabsList class="grid w-full grid-cols-2 bg-card md:grid-cols-1">
            <TabsTrigger
              v-for="tab in tabs"
              :key="tab.value"
              :value="tab.value"
              class="h-12 justify-start data-[state=active]:bg-primary data-[state=active]:text-primary-foreground"
            >
              {{ tab.label }}
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </Card>
      <Card class="w-full min-w-0 flex-auto p-4 md:ml-4 md:w-5/6 md:p-8">
        <slot name="content"></slot>
      </Card>
    </div>
  </Page>
</template>
