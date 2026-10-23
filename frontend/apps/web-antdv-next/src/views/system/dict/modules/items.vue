<script setup lang="ts">
import type { DictItem, DictType } from '#/api/system/dict';

import { nextTick, ref, watch } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';

import { Button, message, Modal } from 'antdv-next';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import { dictApi } from '#/api/system/dict';
import { $t } from '#/locales';

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
    columns: itemColumns(onItemStatusChange),
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
watch(
  () => preferences.app.locale,
  () => {
    itemGrid.setGridOptions({ columns: itemColumns(onItemStatusChange) });
    itemGrid.formApi.setState({ schema: itemSearch() });
  },
);
function onItemStatusChange(status: 0 | 1, row: DictItem): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      cancelText: $t('system.dict.cancel'),
      content: $t('system.dict.statusChangeConfirm', {
        name: row.label,
        status: $t(status === 1 ? 'common.enabled' : 'common.disabled'),
      }),
      okText: $t('system.dict.confirm'),
      onCancel: () => resolve(false),
      onOk: async () => {
        try {
          await dictApi.updateItemStatus(row.id, status);
          message.success($t('system.dict.statusChangeSuccess'));
          await itemGrid.query();
          resolve(true);
        } catch {
          resolve(false);
        }
      },
      title: $t('system.dict.statusChangeTitle'),
    });
  });
}
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
  message.success($t('system.dict.deleteSuccess'));
  await itemGrid.query();
}
</script>

<template>
  <Drawer
    :title="`${$t('system.dict.configItems')} · ${selected?.name ?? ''}`"
    placement="right"
    class="w-[800px] max-w-full"
    content-class="min-h-0 overflow-hidden p-0"
    :footer="false"
  >
    <ItemGrid :table-title="$t('system.dict.itemList')" class="h-full">
      <template #toolbar-tools>
        <Button
          v-if="access.accessCodes.includes('system:dict:create')"
          type="primary"
          @click="editItem()"
        >
          <Plus class="size-5" />
          {{ $t('system.dict.addItem') }}
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
              text: $t('system.dict.edit'),
              auth: 'system:dict:update',
              onClick: () => editItem(row),
            },
            {
              text: $t('system.dict.delete'),
              auth: 'system:dict:delete',
              danger: true,
              popConfirm: {
                okText: $t('system.dict.confirm'),
                cancelText: $t('system.dict.cancel'),
                title: $t('system.dict.deleteItemConfirm'),
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
