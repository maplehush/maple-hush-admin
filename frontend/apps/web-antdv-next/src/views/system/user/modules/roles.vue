<script lang="ts" setup>
import type { UserRecord } from '#/api/system/user';

import { nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { useAccessStore } from '@vben/stores';

import { message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import {
  assignUserRoles,
  getAssignableRoleOptions,
  getUser,
} from '#/api/system/user';
import { $t } from '#/locales';

import { canManageProtectedAction, parseRoleIds } from '../data';

const emits = defineEmits(['success']);
const access = useAccessStore();
const canListRole = () => access.accessCodes.includes('system:role:list');
const target = ref<UserRecord>();
const error = ref('');
const ready = ref(false);
const [Form, formApi] = useVbenForm({
  layout: 'vertical',
  schema: [],
  showDefaultActions: false,
  wrapperClass: 'grid-cols-1',
});
const [Drawer, drawerApi] = useVbenDrawer<UserRecord>({
  async onConfirm() {
    if (
      !ready.value ||
      !target.value ||
      !canManageProtectedAction(target.value)
    )
      return;
    const values = await formApi.getValues();
    let ids: string[];
    try {
      ids = parseRoleIds(
        canListRole()
          ? (values.roleIds ?? []).join(',')
          : (values.roleIds ?? ''),
      );
    } catch (cause) {
      error.value = (cause as Error).message;
      return;
    }
    drawerApi.lock();
    try {
      await assignUserRoles(target.value.id, ids);
      message.success($t('system.user.errors.rolesSave'));
      emits('success');
      drawerApi.close();
    } catch {
      error.value = $t('system.user.errors.rolesSaveFailed');
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    ready.value = false;
    error.value = '';
    target.value = undefined;
    drawerApi.setState({ loading: true, showConfirmButton: false });
    try {
      const current = await getUser(drawerApi.getData()!.id);
      if (!canManageProtectedAction(current)) {
        error.value = $t('system.user.errors.protectedRoles');
        return;
      }
      target.value = current;
      const roles = canListRole() ? await getAssignableRoleOptions() : [];
      const options = roles.map((role) => ({
        value: role.id,
        label: `${role.name}${role.status === 0 ? $t('system.user.disabledRoleSuffix') : ''}`,
        disabled: role.status !== 1 && !current.roleIds.includes(role.id),
      }));
      for (const id of current.roleIds)
        if (!options.some((option) => option.value === id))
          options.push({
            value: id,
            label: $t('system.user.roleExistingId', { id }),
            disabled: false,
          });
      formApi.setState({
        schema: [
          {
            component: canListRole() ? 'Select' : 'Input',
            componentProps: canListRole()
              ? { mode: 'multiple', options, class: 'w-full' }
              : {
                  class: 'w-full',
                  placeholder: $t('system.user.roleIdPlaceholder'),
                },
            fieldName: 'roleIds',
            formItemClass: 'w-full',
            label: $t('system.role.name'),
          },
        ],
      });
      await nextTick();
      await formApi.setValues({
        roleIds: canListRole() ? current.roleIds : current.roleIds.join(','),
      });
      ready.value = true;
      drawerApi.setState({ showConfirmButton: true });
    } catch {
      error.value = $t('system.user.errors.roleOptionsLoad');
    } finally {
      drawerApi.setState({ loading: false });
    }
  },
});
defineExpose({ drawerApi });
</script>
<template>
  <Drawer
    :title="$t('system.role.assignTitle', { username: target?.username ?? '' })"
  >
    <p class="mb-4">
      {{ $t('system.role.assignHelp') }}
    </p>
    <p v-if="!canListRole()" class="mb-4 text-muted-foreground">
      {{ $t('system.role.assignNoListPermission') }}
    </p>
    <Form />
    <p v-if="error" role="alert" class="text-destructive">{{ error }}</p>
  </Drawer>
</template>
