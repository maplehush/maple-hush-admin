<script lang="ts" setup>
import type { UserRecord } from '#/api/system/user';

import { computed, ref } from 'vue';

import { useVbenDrawer, VbenDescriptions } from '@vben/common-ui';

import { getUser } from '#/api/system/user';
import { $t } from '#/locales';

import { useDescriptionItems } from '../data';

const detailData = ref<UserRecord>();
const error = ref('');
const items = computed(() => useDescriptionItems(detailData.value));
const [Drawer, drawerApi] = useVbenDrawer<UserRecord>({
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    detailData.value = undefined;
    error.value = '';
    drawerApi.setState({ loading: true });
    try {
      detailData.value = await getUser(drawerApi.getData()!.id);
    } catch {
      error.value = '读取用户详情失败，请关闭后重试';
    } finally {
      drawerApi.setState({ loading: false });
    }
  },
});
defineExpose({ drawerApi });
</script>
<template>
  <Drawer :footer="false" :title="$t('common.detail')">
    <VbenDescriptions v-if="detailData" bordered :column="1" :items="items" />
    <img
      v-if="detailData?.avatar"
      :src="detailData.avatar"
      alt="用户头像"
      class="mt-4 size-20 rounded object-cover"
    />
    <p v-if="error" role="alert" class="text-destructive">{{ error }}</p>
  </Drawer>
</template>
