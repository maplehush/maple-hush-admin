<script lang="ts" setup>
import type { SystemMenu } from '#/api/system/menu';
import type { RoleSave, SystemRole } from '#/api/system/role';

import { computed, ref } from 'vue';

import { Tree, useVbenDrawer } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { useAccessStore, useUserStore } from '@vben/stores';

import { Alert, message, Spin } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import { getMenuList } from '#/api/system/menu';
import { createRole, getRole, updateRole } from '#/api/system/role';
import { useAuthStore } from '#/store';

import { useFormSchema } from '../data';
import { affectsCurrentRoles, rolePayload } from '../form-model';

const emits = defineEmits(['success']);
const access = useAccessStore();
const user = useUserStore();
const auth = useAuthStore();
const can = (action: string) =>
  access.accessCodes.includes(`system:role:${action}`);
const canMenus = computed(() =>
  access.accessCodes.includes('system:menu:list'),
);
const formData = ref<SystemRole>();
const grantMode = ref(false);
const ready = ref(false);
const [Form, formApi] = useVbenForm({
  schema: useFormSchema(),
  showDefaultActions: false,
});
const permissions = ref<SystemMenu[]>([]);
const loadingPermissions = ref(false);
const [Drawer, drawerApi] = useVbenDrawer<{ id?: string; grant?: boolean }>({
  async onConfirm() {
    if (!ready.value) return;
    if (
      formData.value?.builtIn ||
      (formData.value ? !can('update') : !can('create'))
    )
      return;
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = {
      ...formData.value,
      ...(await formApi.getValues<RoleSave>()),
    };
    const grant =
      grantMode.value || (!formData.value && can('grant') && canMenus.value);
    if (grant && (!can('grant') || !canMenus.value)) return;
    if (formData.value?.status === 0 && values.status === 1 && !can('grant')) {
      message.warning('启用角色需要角色授权权限');
      return;
    }
    if (grant && (values.permissions?.length ?? 0) > 100) {
      message.warning('最多授予100个菜单节点');
      return;
    }
    const payload = rolePayload(values, !formData.value, grant);
    drawerApi.lock();
    try {
      if (formData.value) await updateRole(formData.value.id, payload);
      else await createRole(payload);
      drawerApi.close();
      if (
        formData.value &&
        affectsCurrentRoles(formData.value, payload, user.userRoles)
      ) {
        message.success('当前角色授权或状态已变化，请重新登录');
        await auth.logout();
        return;
      }
      emits('success');
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen) {
    ready.value = false;
    if (!isOpen) return;
    drawerApi.setState({ loading: true, showConfirmButton: false });
    try {
      const data = drawerApi.getData();
      await formApi.reset();
      formData.value = data?.id ? await getRole(data.id) : undefined;
      grantMode.value = !!data?.grant;
      if (
        formData.value?.builtIn ||
        (grantMode.value && (!can('grant') || !canMenus.value))
      ) {
        drawerApi.close();
        return;
      }
      await formApi.updateSchema([
        {
          fieldName: 'roleCode',
          componentProps: { disabled: !!formData.value },
          dependencies: { show: () => !grantMode.value, triggerFields: [] },
        },
        ...['name', 'status', 'remark'].map((fieldName) => ({
          fieldName,
          dependencies: { show: () => !grantMode.value, triggerFields: [] },
        })),
        {
          fieldName: 'status',
          componentProps: {
            options: [
              {
                label: '启用',
                value: 1,
                disabled: formData.value?.status === 0 && !can('grant'),
              },
              { label: '停用', value: 0 },
            ],
            optionType: 'button',
          },
          dependencies: { show: () => !grantMode.value, triggerFields: [] },
        },
        {
          fieldName: 'permissions',
          dependencies: {
            show: () =>
              grantMode.value ||
              (!formData.value && can('grant') && canMenus.value),
            triggerFields: [],
          },
        },
      ]);
      if (
        (grantMode.value || !formData.value) &&
        can('grant') &&
        canMenus.value
      ) {
        loadingPermissions.value = true;
        try {
          permissions.value = await getMenuList();
        } finally {
          loadingPermissions.value = false;
        }
      }
      await formApi.setValues(
        formData.value
          ? {
              ...formData.value,
              remark: formData.value.remark ?? '',
              permissions: [...formData.value.permissions],
            }
          : { roleCode: '', name: '', remark: '', status: 1, permissions: [] },
      );
      ready.value = true;
      drawerApi.setState({ showConfirmButton: true });
    } catch {
      message.error('角色或菜单加载失败，请关闭后重试');
    } finally {
      drawerApi.setState({ loading: false });
    }
  },
});
defineExpose({ drawerApi });
const getDrawerTitle = computed(() =>
  grantMode.value ? '编辑菜单权限' : formData.value ? '编辑角色' : '新增角色',
);
</script>
<template>
  <Drawer :title="getDrawerTitle">
    <Alert
      v-if="can('grant') && !canMenus"
      type="info"
      message="菜单授权依赖 system:menu:list 权限，当前无法读取管理树。"
      class="mb-3"
    />
    <Form>
      <template #permissions="slotProps">
        <Spin :spinning="loadingPermissions" :classes="{ root: 'w-full' }">
          <Tree
            :tree-data="permissions"
            multiple
            check-strictly
            bordered
            :default-expanded-level="2"
            v-bind="slotProps.componentProps"
            value-field="id"
            label-field="meta.title"
            icon-field="meta.icon"
          >
            <template #node="{ value }">
              <IconifyIcon v-if="value.meta.icon" :icon="value.meta.icon" />{{
                value.meta.title
              }}
            </template>
          </Tree>
        </Spin>
      </template>
    </Form>
  </Drawer>
</template>
