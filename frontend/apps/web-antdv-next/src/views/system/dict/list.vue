<script setup lang="ts">
import type { DictItem, DictOption, DictType } from '#/api/system/dict';

import { nextTick, ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { useAccessStore } from '@vben/stores';

import { Button, message } from 'antdv-next';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import { dictApi } from '#/api/system/dict';

import DictTag from './components/dict-tag.vue';
import { itemColumns, itemSearch, typeColumns, typeSearch } from './data';
import Form from './modules/form.vue';
const access = useAccessStore();
const can = (action: string) =>
  access.accessCodes.includes(`system:dict:${action}`);
const selected = ref<DictType>();
const preview = ref<DictOption[]>([]);
const compact = (v: Record<string, unknown>) =>
  Object.fromEntries(
    Object.entries(v).filter(
      ([, x]) => x !== '' && x !== undefined && x !== null,
    ),
  );
const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
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
const [ItemGrid, itemGrid] = useVbenVxeGrid<DictItem>({
  formOptions: { schema: itemSearch() },
  gridOptions: {
    columns: itemColumns,
    height: 360,
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
          const type = selected.value;
          const result = await dictApi.items(
            compact({
              ...values,
              dictTypeId: type.id,
              page: page.currentPage,
              pageSize: page.pageSize,
            }),
          );
          const options = await dictApi.options(type.dictCode);
          if (selected.value?.id === type.id) {
            preview.value = options;
          }
          return result;
        },
      },
    },
    toolbarConfig: { refresh: true, search: true, export: false },
  },
});
async function selectType(row: DictType) {
  selected.value = row;
  preview.value = [];
  await nextTick();
  await itemGrid.reload();
}
function editType(row?: DictType) {
  formDrawerApi.setData({ kind: 'type', id: row?.id }).open();
}
function editItem(row?: DictItem) {
  if (selected.value) {
    formDrawerApi
      .setData({ kind: 'item', id: row?.id, dictTypeId: selected.value.id })
      .open();
  }
}
async function refresh() {
  await typeGrid.query();
  if (selected.value) {
    try {
      selected.value = await dictApi.type(selected.value.id);
      await itemGrid.query();
    } catch {
      selected.value = undefined;
      preview.value = [];
    }
  }
}
async function removeType(row: DictType) {
  await dictApi.removeType(row.id);
  if (selected.value?.id === row.id) {
    selected.value = undefined;
    preview.value = [];
  }
  message.success('删除成功');
  await typeGrid.query();
}
async function removeItem(row: DictItem) {
  await dictApi.removeItem(row.id);
  message.success('删除成功');
  await itemGrid.query();
}
</script>
<template>
  <Page :auto-content-height="!selected">
    <div :class="selected ? 'flex flex-col gap-5' : 'h-full min-h-0'">
      <TypeGrid
        table-title="字典类型"
        :style="{ height: selected ? '360px' : '100%' }"
      >
        <template #toolbar-tools>
          <Button v-if="can('create')" type="primary" @click="editType()">
            新增类型
          </Button>
</template><template #action="{ row }">
          <VbenTableAction
            :actions="[
              { text: '管理字典项', onClick: () => selectType(row) },
              {
                text: '编辑类型',
                auth: 'system:dict:update',
                onClick: () => editType(row),
              },
              {
                text: '删除类型',
                auth: 'system:dict:delete',
                danger: true,
                popConfirm: {
                  okText: '确认',
                  cancelText: '取消',
                  title: '确认删除字典类型？请先删除其下的字典项。',
                  confirm: () => removeType(row),
                },
              },
            ]"
          />
        </template>
      </TypeGrid>
      <section v-if="selected">
        <ItemGrid :table-title="`字典项 · ${selected.name}`">
          <template #toolbar-tools>
            <Button v-if="can('create')" type="primary" @click="editItem()">
              新增字典项
            </Button>
</template><template #label="{ row }"><DictTag :option="row" /></template><template #action="{ row }">
            <VbenTableAction
              :actions="[
                {
                  text: '编辑字典项',
                  auth: 'system:dict:update',
                  onClick: () => editItem(row),
                },
                {
                  text: '删除字典项',
                  auth: 'system:dict:delete',
                  danger: true,
                  popConfirm: {
                    okText: '确认',
                    cancelText: '取消',
                    title: '确认删除该字典项？',
                    confirm: () => removeItem(row),
                  },
                },
              ]"
            />
          </template>
        </ItemGrid>
        <div class="bg-card mt-3 rounded p-4">
          <p class="mb-2">启用项预览：</p>
          <DictTag
            v-for="option in preview"
            :key="option.value"
            :option="option"
          /><span v-if="!preview.length">暂无启用项</span>
        </div>
      </section>
    </div>
    <FormDrawer @success="refresh" />
  </Page>
</template>
