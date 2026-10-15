<script setup lang="ts">
import type { AuditRow, LogKind } from '#/api/system/log';

import { Page, useVbenDrawer } from '@vben/common-ui';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import { getAuditLogs } from '#/api/system/log';

import { useColumns, useSearchSchema } from './data';
import { auditQuery } from './model';
import Detail from './modules/detail.vue';
const props = defineProps<{ kind: LogKind }>();
const [DetailDrawer, detailApi] = useVbenDrawer({
  connectedComponent: Detail,
  destroyOnClose: true,
});
const [Grid] = useVbenVxeGrid<AuditRow>({
  formOptions: { schema: useSearchSchema(props.kind) },
  gridOptions: {
    columns: useColumns(props.kind),
    height: 'auto',
    rowConfig: { keyField: 'id' },
    proxyConfig: {
      ajax: {
        query: async (
          { page }: { page: { currentPage: number; pageSize: number } },
          values: Record<string, unknown>,
        ) =>
          getAuditLogs(
            props.kind,
            auditQuery(props.kind, values, page.currentPage, page.pageSize),
          ),
      },
    },
    toolbarConfig: { refresh: true, search: true, export: false, zoom: true },
  },
});
</script>
<template>
  <Page auto-content-height>
<Grid :table-title="kind === 'login-log' ? '登录日志列表' : '操作日志列表'">
<template #action="{ row }">
<VbenTableAction
          :actions="[
            {
              text: '查看详情',
              onClick: () => detailApi.setData({ kind, row }).open(),
            },
          ]"
/>
</template>
</Grid><DetailDrawer />
</Page>
</template>
