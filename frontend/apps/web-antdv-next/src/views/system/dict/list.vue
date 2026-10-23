<script setup lang="ts">
import type { DictType } from '#/api/system/dict';

import { watch } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';

import { Button, message, Modal } from 'antdv-next';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import { dictApi } from '#/api/system/dict';
import { $t } from '#/locales';

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
    columns: typeColumns(onTypeStatusChange),
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
watch(
  () => preferences.app.locale,
  () => {
    typeGrid.setGridOptions({ columns: typeColumns(onTypeStatusChange) });
    typeGrid.formApi.setState({ schema: typeSearch() });
  },
);
function onTypeStatusChange(status: 0 | 1, row: DictType): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      cancelText: $t('system.dict.cancel'),
      content: $t('system.dict.statusChangeConfirm', {
        name: row.name,
        status: $t(status === 1 ? 'common.enabled' : 'common.disabled'),
      }),
      okText: $t('system.dict.confirm'),
      onCancel: () => resolve(false),
      onOk: async () => {
        try {
          await dictApi.updateTypeStatus(row.id, status);
          message.success($t('system.dict.statusChangeSuccess'));
          await typeGrid.query();
          resolve(true);
        } catch {
          resolve(false);
        }
      },
      title: $t('system.dict.statusChangeTitle'),
    });
  });
}
function editType(row?: DictType) {
  formDrawerApi.setData({ kind: 'type', id: row?.id }).open();
}
async function removeType(row: DictType) {
  await dictApi.removeType(row.id);
  message.success($t('system.dict.deleteSuccess'));
  await typeGrid.query();
}
</script>

<template>
  <Page auto-content-height>
    <TypeGrid :table-title="$t('system.dict.typeList')" class="h-full">
      <template #toolbar-tools>
        <Button v-if="can('create')" type="primary" @click="editType()">
          <Plus class="size-5" />
          {{ $t('system.dict.addType') }}
        </Button>
      </template>
      <template #action="{ row }">
        <VbenTableAction
          :actions="[
            {
              text: $t('system.dict.configItems'),
              onClick: () => itemsDrawerApi.setData(row).open(),
            },
            {
              text: $t('system.dict.edit'),
              auth: 'system:dict:update',
              onClick: () => editType(row),
            },
            {
              text: $t('system.dict.delete'),
              auth: 'system:dict:delete',
              danger: true,
              popConfirm: {
                okText: $t('system.dict.confirm'),
                cancelText: $t('system.dict.cancel'),
                title: $t('system.dict.deleteTypeConfirm'),
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
