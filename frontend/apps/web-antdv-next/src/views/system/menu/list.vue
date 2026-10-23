<script lang="ts" setup>
import type {
  OnActionClickParams,
  VxeTableGridOptions,
} from '#/adapter/vxe-table';

import { ref, watch } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';

import { MenuBadge } from '@vben-core/menu-ui';

import { Alert, Button, message } from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteMenu, getMenuList, SystemMenuApi } from '#/api/system/menu';
import { $t } from '#/locales';
import { refreshAccessRoutes } from '#/router/refresh-access';

import { useColumns } from './data';
import { collectUndeletableMenuIds } from './data';
import Form from './modules/form.vue';

const access = useAccessStore();
const can = (action: string) =>
  access.accessCodes.includes(`system:menu:${action}`);
const undeletableMenuIds = ref<Set<string>>(new Set());
const isDeleteDisabled = (row: SystemMenuApi.SystemMenu) =>
  undeletableMenuIds.value.has(row.id);

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: useColumns(onActionClick, isDeleteDisabled),
    height: 'auto',
    keepSource: true,
    pagerConfig: {
      enabled: false,
    },
    proxyConfig: {
      ajax: {
        query: async (_params) => {
          if (!can('list')) return [];
          const menus = await getMenuList();
          undeletableMenuIds.value = collectUndeletableMenuIds(menus);
          gridApi.setGridOptions({
            columns: useColumns(onActionClick, isDeleteDisabled),
          });
          return menus;
        },
      },
    },
    rowConfig: {
      keyField: 'id',
    },
    toolbarConfig: {
      custom: true,
      export: false,
      refresh: true,
      zoom: true,
    },
    treeConfig: {
      parentField: 'pid',
      rowField: 'id',
      transform: false,
    },
  } as VxeTableGridOptions,
});
watch(
  () => preferences.app.locale,
  () => {
    gridApi.setGridOptions({
      columns: useColumns(onActionClick, isDeleteDisabled),
    });
  },
);

function onActionClick({
  code,
  row,
}: OnActionClickParams<SystemMenuApi.SystemMenu>) {
  switch (code) {
    case 'append': {
      onAppend(row);
      break;
    }
    case 'delete': {
      if (isDeleteDisabled(row)) break;
      onDelete(row);
      break;
    }
    case 'edit': {
      onEdit(row);
      break;
    }
    default: {
      break;
    }
  }
}

async function onRefresh() {
  await gridApi.query();
}
function onEdit(row: SystemMenuApi.SystemMenu) {
  if (can('update')) formDrawerApi.setData(row).open();
}
function onCreate() {
  if (can('create')) formDrawerApi.setData({ type: 'catalog' }).open();
}
function onAppend(row: SystemMenuApi.SystemMenu) {
  if (can('create') && row.type !== 'button')
    formDrawerApi
      .setData({
        pid: String(row.id),
        type: row.type === 'catalog' ? 'menu' : 'button',
      })
      .open();
}

async function onDelete(row: SystemMenuApi.SystemMenu) {
  if (!can('delete') || isDeleteDisabled(row)) return;
  await deleteMenu(row.id);
  await onRefresh();
  try {
    await refreshAccessRoutes();
    message.success($t('system.menu.deleted'));
  } catch {
    message.warning($t('system.menu.refreshFailed'));
  }
}
</script>
<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <Alert
      v-if="!can('list')"
      type="warning"
      :message="$t('system.menu.missingListPermission')"
    />
    <Grid v-else>
      <template #toolbar-tools>
        <Button v-if="can('create')" type="primary" @click="onCreate">
          <Plus class="size-5" />
          {{ $t('ui.actionTitle.create', [$t('system.menu.name')]) }}
        </Button>
      </template>
      <template #title="{ row }">
        <div class="flex w-full items-center gap-1">
          <div class="size-5 shrink-0">
            <IconifyIcon
              v-if="row.type === 'button'"
              icon="carbon:security"
              class="size-full"
            />
            <IconifyIcon
              v-else-if="row.meta?.icon"
              :icon="row.meta?.icon || 'carbon:circle-dash'"
              class="size-full"
            />
          </div>
          <span class="flex-auto">{{ $t(row.meta?.title) }}</span>
          <div class="items-center justify-end"></div>
        </div>
        <MenuBadge
          v-if="row.meta?.badgeType"
          class="menu-badge"
          :badge="row.meta.badge"
          :badge-type="row.meta.badgeType"
          :badge-variants="row.meta.badgeVariants"
        />
      </template>
    </Grid>
  </Page>
</template>
<style lang="scss" scoped>
.menu-badge {
  top: 50%;
  right: 0;
  transform: translateY(-50%);

  & > :deep(div) {
    padding-top: 0;
    padding-bottom: 0;
  }
}
</style>
