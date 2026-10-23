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
import { $t } from '#/locales';
import { useAuthStore } from '#/store';

import { useFormSchema } from '../data';
import {
  affectsCurrentRoles,
  mergeRoleValues,
  roleGrantMenuTree,
  rolePayload,
  rolePermissionTreeProps,
  rolePermissionValues,
} from '../data';

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
  schema: useFormSchema({ mode: 'create' }),
  showDefaultActions: false,
});
const permissions = ref<SystemMenu[]>([]);
const allPermissions = ref<SystemMenu[]>([]);
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
    const values = mergeRoleValues(
      formData.value,
      await formApi.getValues<RoleSave>(),
      grantMode.value,
    );
    const grant =
      grantMode.value || (!formData.value && can('grant') && canMenus.value);
    if (grant && (!can('grant') || !canMenus.value)) return;
    if (formData.value?.status === 0 && values.status === 1 && !can('grant')) {
      message.warning($t('system.role.grantRequired'));
      return;
    }
    if (grant && (values.permissions?.length ?? 0) > 100) {
      message.warning($t('system.role.maxMenuNodes'));
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
        message.success($t('system.role.changedRelogin'));
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
      permissions.value = [];
      allPermissions.value = [];
      formData.value = data?.id ? await getRole(data.id) : undefined;
      grantMode.value = !!data?.grant;
      if (
        formData.value?.builtIn ||
        (grantMode.value && (!can('grant') || !canMenus.value))
      ) {
        drawerApi.close();
        return;
      }
      formApi.setState({
        schema: useFormSchema({
          canGrant: can('grant') && canMenus.value,
          mode: grantMode.value ? 'grant' : formData.value ? 'edit' : 'create',
          roleCodeDisabled: !!formData.value,
          statusDisabled: formData.value?.status === 0 && !can('grant'),
        }),
      });
      if (
        (grantMode.value || !formData.value) &&
        can('grant') &&
        canMenus.value
      ) {
        loadingPermissions.value = true;
        try {
          allPermissions.value = await getMenuList();
          permissions.value = roleGrantMenuTree(allPermissions.value);
        } finally {
          loadingPermissions.value = false;
        }
      }
      await formApi.setValues(
        formData.value
          ? {
              ...formData.value,
              remark: formData.value.remark ?? '',
              permissions: rolePermissionValues(
                permissions.value,
                formData.value.permissions,
              ),
            }
          : { roleCode: '', name: '', remark: '', status: 1, permissions: [] },
      );
      ready.value = true;
      drawerApi.setState({ showConfirmButton: true });
    } catch {
      message.error($t('system.role.errors.load'));
    } finally {
      drawerApi.setState({ loading: false });
    }
  },
});
defineExpose({ drawerApi });
const getDrawerTitle = computed(() =>
  grantMode.value
    ? $t('system.role.editPermissions')
    : formData.value
      ? $t('system.role.edit')
      : $t('system.role.create'),
);
</script>
<template>
  <Drawer :title="getDrawerTitle" content-class="overflow-hidden">
    <Alert
      v-if="!formData && can('grant') && !canMenus"
      type="info"
      :message="$t('system.role.noMenuPermission')"
      class="mb-3"
    />
    <Form>
      <template #permissions="slotProps">
        <div
          class="role-permissions-container w-full min-w-0 overflow-y-auto overscroll-contain"
          :class="{ 'role-permissions-container--grant': grantMode }"
        >
          <Spin :spinning="loadingPermissions" :classes="{ root: 'w-full' }">
            <Tree
              class="role-permissions-tree w-full"
              :tree-data="permissions"
              bordered
              :default-expanded-level="2"
              v-bind="{
                ...slotProps.componentProps,
                ...rolePermissionTreeProps,
              }"
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
        </div>
      </template>
    </Form>
  </Drawer>
</template>

<style scoped>
.role-permissions-container {
  max-height: 30rem;
}

.role-permissions-container--grant {
  height: calc(100dvh - 10rem);
  max-height: none;
}

:deep(.role-permissions-tree > div:has(.item-all-checkbox)) {
  position: sticky;
  top: 0;
  z-index: 1;
  background-color: hsl(var(--background));
}
</style>
