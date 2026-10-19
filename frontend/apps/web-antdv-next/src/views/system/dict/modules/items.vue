<script setup lang="ts">
import type { DictItem, DictType } from '#/api/system/dict';

import { nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

import { Button, message } from 'antdv-next';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import { dictApi } from '#/api/system/dict';

import { itemColumns, itemSearch } from '../data';
import Form from './form.vue';

const selected = ref<DictType>();
const access = useAccessStore();
const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [ItemGrid, itemGrid] = useVbenVxeGrid<DictItem>({
  formOptions: { schema: itemSearch() },
  gridOptions: {
    columns: itemColumns,
    height: 'auto',
    rowConfig: { keyField: 'id' },
    proxyConfig: {
      autoLoad: false,
      ajax: {
        query: async (
          { page }: { page: { currentPage: number; pageSize: number } },
          values: Record<string, unknown>,
        ) => {
          if (!selected.value) {
            return { items: [], total: 0 };
          }
          return dictApi.items(
            Object.fromEntries(
              Object.entries({
                ...values,
                dictTypeId: selected.value.id,
                page: page.currentPage,
                pageSize: page.pageSize,
              }).filter(([, value]) => value !== '' && value != null),
            ),
          );
        },
      },
    },
    toolbarConfig: { refresh: true, search: true, export: false },
  },
});
const [Drawer, drawerApi] = useVbenDrawer<DictType>({
  async onOpenChange(open) {
    if (!open) {
      return;
    }
    selected.value = drawerApi.getData();
    await nextTick();
    await itemGrid.formApi.reset();
    await itemGrid.reload();
  },
});
function editItem(row?: DictItem) {
  if (selected.value) {
    formDrawerApi
      .setData({ kind: 'item', id: row?.id, dictTypeId: selected.value.id })
      .open();
  }
}
async function removeItem(row: DictItem) {
  await dictApi.removeItem(row.id);
  message.success('删除成功');
  await itemGrid.query();
}
</script>

<template>
  <Drawer
    :title="`字典项配置 · ${selected?.name ?? ''}`"
    placement="left"
    class="w-[1040px] max-w-full"
    content-class="min-h-0 overflow-hidden p-0"
    :footer="false"
  >
    <ItemGrid table-title="字典项" class="h-full">
      <template #toolbar-tools>
        <Button
          v-if="access.accessCodes.includes('system:dict:create')"
          type="primary"
          @click="editItem()"
        >
          <Plus class="size-5" />
          新增字典项
        </Button>
      </template>
      <template #color="{ row }">
        <span v-if="row.color" class="inline-flex items-center gap-2">
          <span
            class="border-border size-3 shrink-0 rounded-sm border"
            :style="{ backgroundColor: row.color }"
            aria-hidden="true"
          ></span>
          {{ row.color }}
        </span>
        <span v-else>—</span>
      </template>
      <template #action="{ row }">
        <VbenTableAction
          :actions="[
            {
              text: '编辑',
              auth: 'system:dict:update',
              onClick: () => editItem(row),
            },
            {
              text: '删除',
              auth: 'system:dict:delete',
              danger: true,
              popConfirm: {
                okText: '确认',
                cancelText: '取消',
                title: '确认删除？',
                confirm: () => removeItem(row),
              },
            },
          ]"
        />
      </template>
    </ItemGrid>
    <FormDrawer @success="itemGrid.query" />
  </Drawer>
</template>
