<script setup lang="ts">
import type { AuditRow, LogKind } from '#/api/system/log';

import { watch } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { preferences } from '@vben/preferences';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import { getAuditLogs } from '#/api/system/log';
import { $t } from '#/locales';

import { useColumns, useSearchSchema } from './data';
import { auditQuery } from './data';
import Detail from './modules/detail.vue';
const props = defineProps<{ kind: LogKind }>();
const [DetailDrawer, detailApi] = useVbenDrawer({
  connectedComponent: Detail,
  destroyOnClose: true,
});
const [Grid, gridApi] = useVbenVxeGrid<AuditRow>({
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
watch(
  () => preferences.app.locale,
  () => {
    gridApi.setGridOptions({ columns: useColumns(props.kind) });
    gridApi.formApi.setState({ schema: useSearchSchema(props.kind) });
  },
);
</script>
<template>
  <Page auto-content-height>
    <Grid
      :table-title="
        kind === 'login-log'
          ? $t('system.log.loginList')
          : $t('system.log.operationList')
      "
    >
      <template #action="{ row }">
        <VbenTableAction
          :actions="[
            {
              text: $t('system.log.viewDetail'),
              onClick: () => detailApi.setData({ kind, row }).open(),
            },
          ]"
        />
      </template>
    </Grid>
    <DetailDrawer />
  </Page>
</template>
