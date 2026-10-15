<script setup lang="ts">
import type { AuditRow, LogKind } from '#/api/system/log';

import { computed, ref } from 'vue';

import { useVbenDrawer, VbenDescriptions } from '@vben/common-ui';

import { detailFields, detailLabels } from '../data';
const row = ref<AuditRow>();
const kind = ref<LogKind>('login-log');
const items = computed(() =>
  detailFields[kind.value].map((field) => ({
    label: detailLabels[field],
    content:
      field === 'success'
        ? row.value?.[field]
          ? '成功'
          : '失败'
        : String(row.value?.[field] ?? '—'),
  })),
);
const [Drawer, drawerApi] = useVbenDrawer<{ kind: LogKind; row: AuditRow }>({
  onOpenChange(open) {
    if (open) {
      const data = drawerApi.getData();
      if (data) {
        row.value = data.row;
        kind.value = data.kind;
      }
    }
  },
});
</script>
<template>
  <Drawer title="日志详情" :footer="false"
    ><VbenDescriptions bordered :column="1" :items="items"
  /></Drawer>
</template>
