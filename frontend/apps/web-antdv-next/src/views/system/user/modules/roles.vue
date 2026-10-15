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

import { canManageProtectedAction, parseRoleIds } from '../form-policy';

const emits = defineEmits(['success']);
const access = useAccessStore();
const canListRole = () => access.accessCodes.includes('system:role:list');
const target = ref<UserRecord>();
const error = ref('');
const ready = ref(false);
const [Form, formApi] = useVbenForm({ schema: [], showDefaultActions: false });
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
      message.success('角色分配已保存');
      emits('success');
      drawerApi.close();
    } catch {
      error.value = '分配失败，选择已保留；请检查权限或重新登录后重试';
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
        error.value = '唯一超级管理员角色不可修改';
        return;
      }
      target.value = current;
      const roles = canListRole() ? await getAssignableRoleOptions() : [];
      const options = roles.map((role) => ({
        value: role.id,
        label: `${role.name}${role.status === 0 ? '（停用）' : ''}`,
        disabled: role.status !== 1 && !current.roleIds.includes(role.id),
      }));
      for (const id of current.roleIds)
        if (!options.some((option) => option.value === id))
          options.push({
            value: id,
            label: `ID ${id}（已有）`,
            disabled: false,
          });
      formApi.setState({
        schema: [
          {
            component: canListRole() ? 'Select' : 'Input',
            componentProps: canListRole()
              ? { mode: 'multiple', options }
              : { placeholder: '正整数角色ID，以逗号分隔；留空清空' },
            fieldName: 'roleIds',
            label: '角色',
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
      error.value = '读取用户或角色选项失败，请关闭后重试';
    } finally {
      drawerApi.setState({ loading: false });
    }
  },
});
defineExpose({ drawerApi });
</script>
<template>
  <Drawer :title="`分配角色：${target?.username ?? ''}`">
    <p class="mb-4">
      全量替换角色；留空清除。角色实际变化后目标用户需要重新登录。
    </p>
    <p v-if="!canListRole()" class="mb-4 text-muted-foreground">
      无角色查询权限，原角色ID已保留；ID与可授予范围由后端校验。
    </p>
    <Form />
    <p v-if="error" role="alert" class="text-destructive">{{ error }}</p>
  </Drawer>
</template>
