<script lang="ts" setup>
import type { Dayjs } from 'dayjs';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DeptRecord } from '#/api/system/dept';
import type { UserRecord } from '#/api/system/user';

import { computed, onMounted, ref, watch } from 'vue';

import { Page, Tree, useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';

import { Button, Card, InputSearch, message, Modal } from 'antdv-next';

import { useVbenVxeGrid, VbenTableAction } from '#/adapter/vxe-table';
import { getDeptList } from '#/api/system/dept';
import { deleteUser, getUserList, resetUserPassword } from '#/api/system/user';
import { $t } from '#/locales';
import { createDateRangeCodec } from '#/utils/date-range-codec';

import { useColumns, useGridFormSchema } from './data';
import { canManageProtectedAction, departmentIdFromTreeEvent } from './data';
import Detail from './modules/detail.vue';
import Form from './modules/form.vue';
import Roles from './modules/roles.vue';

interface UserSearchFormValues extends Record<string, unknown> {
  createTime?: [Dayjs, Dayjs];
}

const userSearchCodec = createDateRangeCodec<UserSearchFormValues>()({
  endField: 'endTime',
  rangeField: 'createTime',
  startField: 'startTime',
});

type UserSearchSubmitValues = ReturnType<typeof userSearchCodec.encode>;

const access = useAccessStore();
const can = (action: string) =>
  access.accessCodes.includes(`system:user:${action}`);
const canListDept = computed(() =>
  access.accessCodes.includes('system:dept:list'),
);
const deptList = ref<DeptRecord[]>([]);
const inputSearchValue = ref('');
const selectedDeptId = ref<string>('');
const filteredDepts = computed(() => {
  const filter = (nodes: DeptRecord[]): DeptRecord[] =>
    nodes.flatMap((node) => {
      const children = filter(node.children);
      return node.name
        .toLowerCase()
        .includes(inputSearchValue.value.toLowerCase())
        ? [node]
        : children.length
          ? [{ ...node, children }]
          : [];
    });
  return filter(deptList.value);
});
const [RolesDrawer, rolesDrawerApi] = useVbenDrawer({
  connectedComponent: Roles,
  destroyOnClose: true,
});

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [DetailDrawer, detailDrawerApi] = useVbenDrawer({
  connectedComponent: Detail,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    codec: userSearchCodec,
    schema: useGridFormSchema(),
    submitOnChange: true,
  },
  gridOptions: {
    columns: useColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: { pageSizes: [10, 20, 50, 100] },
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues: UserSearchSubmitValues) => {
          const result = await getUserList({
            page: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
            deptId: selectedDeptId.value || undefined,
          });
          return result;
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
      search: true,
      zoom: true,
    },
  } as VxeTableGridOptions<UserRecord>,
});
watch(
  () => preferences.app.locale,
  () => {
    gridApi.setGridOptions({ columns: useColumns() });
    gridApi.formApi.setState({ schema: useGridFormSchema() });
  },
);

/**
 * 将Antd的Modal.confirm封装为promise，方便在异步函数中调用。
 * @param content 提示内容
 * @param title 提示标题
 */
function confirm(content: string, title: string) {
  return new Promise((reslove, reject) => {
    Modal.confirm({
      content,
      onCancel() {
        reject(new Error('已取消'));
      },
      onOk() {
        reslove(true);
      },
      title,
    });
  });
}

function onEdit(row: UserRecord) {
  formDrawerApi.setData(row).open();
}

function onDetail(row: UserRecord) {
  detailDrawerApi.setData(row).open();
}

function onDelete(row: UserRecord) {
  if (!canManageProtectedAction(row)) return;
  const hideLoading = message.loading({
    content: $t('ui.actionMessage.deleting', [row.name]),
    duration: 0,
    key: 'action_process_msg',
  });
  deleteUser(row.id)
    .then(() => {
      message.success({
        content: $t('ui.actionMessage.deleteSuccess', [row.name]),
        key: 'action_process_msg',
      });
      onRefresh();
    })
    .catch(() => {
      hideLoading();
    });
}

function onRefresh() {
  gridApi.query();
}

function onCreate() {
  formDrawerApi.setData(null).open();
}

async function loadDeptList() {
  if (!canListDept.value) return;
  try {
    const res = await getDeptList();
    deptList.value = res;
  } catch (error) {
    message.error($t('system.user.errors.deptLoad'));
  }
}

function selectDept(event?: unknown) {
  try {
    const clickedDeptId = departmentIdFromTreeEvent(event);
    selectedDeptId.value =
      clickedDeptId && clickedDeptId !== selectedDeptId.value
        ? clickedDeptId
        : '';
    gridApi.query();
  } catch (cause) {
    message.error((cause as Error).message);
  }
}

onMounted(() => {
  loadDeptList();
});
function onRoles(row: UserRecord) {
  if (canManageProtectedAction(row)) rolesDrawerApi.setData(row).open();
}
async function onReset(row: UserRecord) {
  if (!canManageProtectedAction(row)) return;
  try {
    await confirm(
      $t('system.user.resetConfirmContent'),
      $t('system.user.resetConfirmTitle', { username: row.username }),
    );
    await resetUserPassword(row.id);
    message.success($t('system.user.resetPasswordSuccess'));
  } catch {
    /* 取消或失败保留列表；请求层展示错误。 */
  }
}
function rowActions(row: UserRecord, dropdown = false) {
  const actions = [];
  if (!dropdown && can('list'))
    actions.push({
      text: $t('common.detail'),
      icon: 'lucide:eye',
      onClick: () => onDetail(row),
    });
  if (!dropdown && can('update'))
    actions.push({
      text: $t('common.edit'),
      icon: 'lucide:edit',
      onClick: () => onEdit(row),
    });
  if (dropdown && can('assign-role') && canManageProtectedAction(row))
    actions.push({
      text: $t('system.user.assignRoles'),
      onClick: () => onRoles(row),
    });
  if (dropdown && can('reset-password') && canManageProtectedAction(row))
    actions.push({
      text: $t('system.user.resetPassword'),
      onClick: () => onReset(row),
    });
  if (dropdown && can('delete') && canManageProtectedAction(row))
    actions.push({
      text: $t('common.delete'),
      icon: 'lucide:trash-2',
      danger: true,
      popConfirm: {
        title: $t('ui.actionMessage.deleteConfirm', [row.name]),
        confirm: () => onDelete(row),
      },
      auth: ['system:user:delete'],
    });
  return actions;
}
</script>
<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <DetailDrawer @success="onRefresh" />
    <RolesDrawer @success="onRefresh" />
    <div class="flex size-full gap-4">
      <Card v-if="canListDept" class="w-60 shrink-0">
        <InputSearch
          v-model:value="inputSearchValue"
          :placeholder="$t('system.user.placeholder')"
        />
        <div class="mt-4">
          <Tree
            allow-clear
            :model-value="selectedDeptId || undefined"
            label-field="name"
            value-field="id"
            :tree-data="filteredDepts"
            :default-expanded-level="2"
            @select="selectDept"
          />
        </div>
      </Card>
      <div class="min-w-0 flex-1">
        <Grid :table-title="$t('system.user.list')">
          <template #toolbar-tools>
            <Button v-if="can('create')" type="primary" @click="onCreate">
              <Plus class="size-5" />
              {{ $t('ui.actionTitle.create', [$t('system.user.entity')]) }}
            </Button>
          </template>
          <template #action="{ row }">
            <VbenTableAction
              :actions="rowActions(row)"
              :more-text="$t('system.user.more')"
              :dropdown-actions="rowActions(row, true)"
              align="center"
            />
          </template>
        </Grid>
      </div>
    </div>
  </Page>
</template>
