<script lang="ts" setup>
import type { Dayjs } from 'dayjs';

import type {
  OnActionClickParams,
  VxeTableGridOptions,
} from '#/adapter/vxe-table';
import type { SystemRole } from '#/api/system/role';

import { watch } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';
import { preferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';

import { Alert, Button, message, Modal } from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteRole, getRoleList, updateRoleStatus } from '#/api/system/role';
import { $t } from '#/locales';
import { useAuthStore } from '#/store';
import { createDateRangeCodec } from '#/utils/date-range-codec';

import { useColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

interface RoleSearchFormValues extends Record<string, unknown> {
  createTime?: [Dayjs, Dayjs];
}
const access = useAccessStore();
const user = useUserStore();
const auth = useAuthStore();
const can = (action: string) =>
  access.accessCodes.includes(`system:role:${action}`);
const roleSearchCodec = createDateRangeCodec<RoleSearchFormValues>()({
  endField: 'endTime',
  rangeField: 'createTime',
  startField: 'startTime',
});
type RoleSearchSubmitValues = ReturnType<typeof roleSearchCodec.encode>;
const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});
const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    codec: roleSearchCodec,
    schema: useGridFormSchema(),
    submitOnChange: true,
  },
  gridOptions: {
    columns: useColumns(onActionClick, onStatusChange),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues: RoleSearchSubmitValues) => {
          if (!can('list')) return { items: [], total: 0 };
          return await getRoleList({
            page: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: { keyField: 'id' },
    toolbarConfig: {
      custom: true,
      export: false,
      refresh: true,
      search: true,
      zoom: true,
    },
  } as VxeTableGridOptions<SystemRole>,
});
watch(
  () => preferences.app.locale,
  () => {
    gridApi.setGridOptions({
      columns: useColumns(onActionClick, onStatusChange),
    });
    gridApi.formApi.setState({ schema: useGridFormSchema() });
  },
);
function onActionClick({ code, row }: OnActionClickParams<SystemRole>) {
  if (row.builtIn) return;
  if (code === 'delete' && can('delete')) void onDelete(row);
  if (code === 'edit' && can('update'))
    formDrawerApi.setData({ id: row.id }).open();
  if (
    code === 'grant' &&
    can('grant') &&
    can('update') &&
    access.accessCodes.includes('system:menu:list')
  )
    formDrawerApi.setData({ id: row.id, grant: true }).open();
}
function onStatusChange(status: 0 | 1, row: SystemRole): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      cancelText: $t('common.cancel'),
      content: $t('system.role.statusChangeConfirm', {
        name: row.name,
        status: $t(status === 1 ? 'common.enabled' : 'common.disabled'),
      }),
      okText: $t('common.confirm'),
      onCancel: () => resolve(false),
      onOk: async () => {
        try {
          await updateRoleStatus(row.id, { status });
          if (user.userRoles.includes(row.roleCode)) {
            message.success($t('system.role.changedRelogin'));
            await auth.logout();
          } else {
            message.success($t('system.role.statusChangeSuccess'));
            onRefresh();
          }
          resolve(true);
        } catch {
          resolve(false);
        }
      },
      title: $t('system.role.statusChangeTitle'),
    });
  });
}
async function onDelete(row: SystemRole) {
  if (row.builtIn || !can('delete')) return;
  await deleteRole(row.id);
  message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
  if (user.userRoles.includes(row.roleCode)) {
    message.info($t('system.role.deletedRelogin'));
    await auth.logout();
    return;
  }
  onRefresh();
}
function onRefresh() {
  void gridApi.query();
}
function onCreate() {
  if (can('create')) formDrawerApi.setData({}).open();
}
</script>
<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <Alert
      v-if="!can('list')"
      :message="$t('system.role.missingListPermission')"
      type="warning"
    />
    <template v-else>
      <Alert
        v-if="can('grant') && !access.accessCodes.includes('system:menu:list')"
        type="info"
        :message="$t('system.role.noMenuPermission')"
        class="mb-3"
      />
      <Grid :table-title="$t('system.role.list')">
        <template #toolbar-tools>
          <Button v-if="can('create')" type="primary" @click="onCreate">
            <Plus class="size-5" />{{
              $t('ui.actionTitle.create', [$t('system.role.name')])
            }}
          </Button>
        </template>
      </Grid>
    </template>
  </Page>
</template>
