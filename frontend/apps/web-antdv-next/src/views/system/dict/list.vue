<script setup lang="ts">
import type { DictType } from '#/api/system/dict';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

import { Button, message } from 'antdv-next';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import { dictApi } from '#/api/system/dict';

import { typeColumns, typeSearch } from './data';
import Form from './modules/form.vue';
import Items from './modules/items.vue';

const access = useAccessStore();
const can = (action: string) =>
  access.accessCodes.includes(`system:dict:${action}`);
const compact = (values: Record<string, unknown>) =>
  Object.fromEntries(
    Object.entries(values).filter(([, value]) => value !== '' && value != null),
  );
const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [ItemsDrawer, itemsDrawerApi] = useVbenDrawer({
  connectedComponent: Items,
  destroyOnClose: true,
});
const [TypeGrid, typeGrid] = useVbenVxeGrid<DictType>({
  formOptions: { schema: typeSearch() },
  gridOptions: {
    columns: typeColumns,
    height: 'auto',
    rowConfig: { keyField: 'id' },
    proxyConfig: {
      ajax: {
        query: async (
          { page }: { page: { currentPage: number; pageSize: number } },
          values: Record<string, unknown>,
        ) =>
          dictApi.types(
            compact({
              ...values,
              page: page.currentPage,
              pageSize: page.pageSize,
            }),
          ),
      },
    },
    toolbarConfig: { refresh: true, search: true, export: false },
  },
});
function editType(row?: DictType) {
  formDrawerApi.setData({ kind: 'type', id: row?.id }).open();
}
async function removeType(row: DictType) {
  await dictApi.removeType(row.id);
  message.success('删除成功');
  await typeGrid.query();
}
</script>

<template>
  <Page auto-content-height>
    <TypeGrid table-title="字典类型" class="h-full">
      <template #toolbar-tools>
        <Button v-if="can('create')" type="primary" @click="editType()">
          <Plus class="size-5" />
          新增类型
        </Button>
      </template>
      <template #action="{ row }">
        <VbenTableAction
          :actions="[
            {
              text: '字典项配置',
              onClick: () => itemsDrawerApi.setData(row).open(),
            },
            {
              text: '编辑',
              auth: 'system:dict:update',
              onClick: () => editType(row),
            },
            {
              text: '删除',
              auth: 'system:dict:delete',
              danger: true,
              popConfirm: {
                okText: '确认',
                cancelText: '取消',
                title: '确认删除？请先删除其下的字典项。',
                confirm: () => removeType(row),
              },
            },
          ]"
        />
      </template>
    </TypeGrid>
    <FormDrawer @success="typeGrid.query" />
    <ItemsDrawer />
  </Page>
</template>
