<script lang="ts" setup>
import type { UploadFile, UploadProps } from 'antdv-next';

import type { UserCreatePayload, UserRecord } from '#/api/system/user';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { useAccessStore } from '@vben/stores';

import { message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import {
  createUser,
  getAssignableRoleOptions,
  getUser,
  updateUser,
  uploadUserImage,
} from '#/api/system/user';
import { $t } from '#/locales';

import { useFormSchema } from '../data';
import {
  avatarUploadErrorKey,
  buildUserPayload,
  canManageProtectedAction,
  parseRoleIds,
  pendingImageFromAvatar,
} from '../data';

const emits = defineEmits(['success']);
const access = useAccessStore();
const canAssign = () => access.accessCodes.includes('system:user:assign-role');
const canListRole = () => access.accessCodes.includes('system:role:list');
const formData = ref<UserRecord>();
const ready = ref(false);
const error = ref('');
const uploading = ref(false);
const id = ref<string>();
const [Form, formApi] = useVbenForm({
  schema: useFormSchema(),
  showDefaultActions: false,
});
const [Drawer, drawerApi] = useVbenDrawer<null | UserRecord>({
  async onConfirm() {
    if (!ready.value || uploading.value) return;
    const { valid } = await formApi.validate();
    if (!valid) return;
    error.value = '';
    const values = await formApi.getValues();
    let payload;
    try {
      const roleIds =
        !id.value && canAssign()
          ? canListRole()
            ? (values.roleIds ?? [])
            : parseRoleIds(values.roleIds ?? '')
          : [];
      payload = buildUserPayload(
        {
          username: values.username ?? '',
          name: values.name ?? '',
          deptId: values.deptId,
          status: values.status,
          remark: values.remark ?? '',
          roleIds,
        },
        !!id.value,
        pendingImageFromAvatar(values.avatar),
      );
      if (
        formData.value &&
        !canManageProtectedAction(formData.value) &&
        payload.status !== formData.value.status
      )
        throw new Error($t('system.user.errors.protectedStatus'));
    } catch (cause) {
      error.value = (cause as Error).message;
      return;
    }
    drawerApi.lock();
    try {
      if (id.value) await updateUser(id.value, payload);
      else await createUser(payload as UserCreatePayload);
      message.success($t('system.user.saved'));
      emits('success');
      drawerApi.close();
    } catch {
      error.value = $t('system.user.errors.save');
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    ready.value = false;
    error.value = '';
    await formApi.reset();
    const data = drawerApi.getData();
    id.value = data?.id;
    formData.value = undefined;
    drawerApi.setState({ showConfirmButton: false, loading: true });
    try {
      const current = data ? await getUser(data.id) : undefined;
      formData.value = current;
      formApi.setState({
        schema: useFormSchema({
          editing: !!current,
          protected: !!current && !canManageProtectedAction(current),
          canAssign: canAssign(),
          canListDept: access.accessCodes.includes('system:dept:list'),
          canListRole: canListRole(),
          enableNeedsAssign: current?.status === 0 && !canAssign(),
          avatarUploadProps: () => ({
            disabled: uploading.value || !ready.value,
            customRequest: uploadAvatar,
            beforeUpload: validateAvatar,
            handleChange: ({ file }) => {
              if (file.status === 'done') {
                message.success($t('system.user.errors.avatarUploaded'));
              } else if (file.status === 'error') {
                message.error(
                  $t(`system.user.errors.${avatarUploadErrorKey(file.error)}`),
                );
              }
            },
            onRemove: removeAvatar,
          }),
        }),
      });
      await nextTick();
      await formApi.setValues({
        username: current?.username ?? '',
        name: current?.name ?? '',
        deptId: current?.deptId ?? undefined,
        roleIds: canListRole() ? [] : '',
        status: current?.status ?? 1,
        remark: current?.remark ?? '',
        avatar: existingAvatarFiles(current?.avatar),
      });
      if (!current && canAssign() && canListRole()) {
        const roles = await getAssignableRoleOptions();
        formApi.updateSchema([
          {
            fieldName: 'roleIds',
            componentProps: {
              mode: 'multiple',
              options: roles.map((role) => ({
                label: `${role.name}${role.status === 0 ? $t('system.user.disabledRoleSuffix') : ''}`,
                value: role.id,
                disabled: role.status !== 1,
              })),
            },
          },
        ]);
      }
      ready.value = true;
      drawerApi.setState({ showConfirmButton: true });
    } catch {
      error.value = $t('system.user.errors.optionsLoad');
    } finally {
      drawerApi.setState({ loading: false });
    }
  },
});
defineExpose({ drawerApi });
const getDrawerTitle = computed(() =>
  formData.value?.id ? $t('system.user.edit') : $t('system.user.create'),
);
function existingAvatarFiles(url?: null | string): UploadFile[] {
  return url
    ? [
        {
          uid: 'existing-avatar',
          name: $t('system.user.existingAvatar'),
          status: 'done',
          url,
        },
      ]
    : [];
}
function validateAvatar(file: File) {
  error.value = '';
  if (
    !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
    !file.size ||
    file.size > 2 * 1024 * 1024
  ) {
    message.error($t('system.user.errors.avatarFiles'));
    Object.assign(file, { status: 'removed' });
    return false;
  }
  return true;
}
function removeAvatar(file: UploadFile) {
  if (file.uid === 'existing-avatar') return false;
  error.value = '';
  if (formData.value?.avatar) {
    void formApi.setValues({
      avatar: existingAvatarFiles(formData.value.avatar),
    });
    return false;
  }
  return true;
}
async function uploadAvatar({
  file,
  onSuccess,
  onError,
  onProgress,
}: Parameters<NonNullable<UploadProps['customRequest']>>[0]) {
  if (!ready.value || uploading.value || !(file instanceof File)) {
    onError?.(new Error($t('system.user.errors.avatarUnavailable')));
    return;
  }
  error.value = '';
  const startedAt = Date.now();
  uploading.value = true;
  drawerApi.lock();
  try {
    onProgress?.({ percent: 0 });
    const result = await uploadUserImage(file);
    Object.assign(file, { url: result.url });
    onProgress?.({ percent: 100 });
    onSuccess?.({ ...result, expiresAt: startedAt + 2 * 60 * 60 * 1000 });
  } catch (cause) {
    onError?.(
      cause instanceof Error
        ? cause
        : new Error($t('system.user.errors.avatarUpload')),
    );
  } finally {
    uploading.value = false;
    drawerApi.unlock();
  }
}
</script>
<template>
  <Drawer :title="getDrawerTitle">
    <Form />
    <p v-if="error" role="alert" class="text-destructive">{{ error }}</p>
  </Drawer>
</template>
