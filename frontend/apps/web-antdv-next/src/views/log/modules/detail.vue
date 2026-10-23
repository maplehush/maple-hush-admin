<script setup lang="ts">
import type { AuditRow, LogKind } from '#/api/system/log';

import { computed, ref } from 'vue';

import { useVbenDrawer, VbenDescriptions } from '@vben/common-ui';

import { $t } from '#/locales';

import { detailFields, detailLabels, loginLogText } from '../data';
const row = ref<AuditRow>();
const kind = ref<LogKind>('login-log');

/**
 * 详情同时展示事件、原因的易读说明和原始编码，方便核对服务端日志。
 */
function detailContent(field: string): string {
  const value = row.value?.[field];
  if (field === 'success') {
    return value ? $t('system.log.success') : $t('system.log.failure');
  }
  if (
    kind.value === 'login-log' &&
    ['eventType', 'reasonCode'].includes(field)
  ) {
    const label = loginLogText(
      field === 'eventType' ? 'events' : 'reasons',
      value,
    );
    return value && label !== value ? `${label} (${value})` : label;
  }
  return String(value ?? '—');
}

const items = computed(() =>
  detailFields[kind.value].map((field) => ({
    label: detailLabels()[field],
    content: detailContent(field),
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
  <Drawer :title="$t('system.log.detail')" :footer="false">
    <VbenDescriptions
      class="log-detail-descriptions"
      bordered
      :column="1"
      :items="items"
    />
  </Drawer>
</template>

<style scoped>
.log-detail-descriptions :deep(table) {
  table-layout: fixed;
}

.log-detail-descriptions :deep(th) {
  width: 117px;
}
</style>
