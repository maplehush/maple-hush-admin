<script lang="ts" setup>
import type { Dayjs } from 'dayjs';

import type {
  OnActionClickParams,
  VxeTableGridOptions,
} from '#/adapter/vxe-table';
import type { SystemRole } from '#/api/system/role';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';
import { useAccessStore, useUserStore } from '@vben/stores';

import { Alert, Button, message } from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteRole, getRoleList } from '#/api/system/role';
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
    columns: useColumns(onActionClick),
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
async function onDelete(row: SystemRole) {
  if (row.builtIn || !can('delete')) return;
  await deleteRole(row.id);
  message.success($t('ui.actionMessage.deleteSuccess', [row.name]));
  if (user.userRoles.includes(row.roleCode)) {
    message.info('当前角色已删除，请重新登录');
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
    <Alert v-if="!can('list')" message="缺少角色查询权限" type="warning" />
    <template v-else>
      <Alert
        v-if="can('grant') && !access.accessCodes.includes('system:menu:list')"
        type="info"
        message="暂无菜单查看权限，无法配置菜单权限。"
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
