<script lang="ts" setup>
import type { PendingImage } from '../form-policy';

import type { UserCreatePayload, UserRecord } from '#/api/system/user';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { Plus } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

import { message, Spin, Upload } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import {
  createUser,
  getAssignableRoleOptions,
  getUser,
  updateUser,
  uploadUserImage,
} from '#/api/system/user';

import { useFormSchema } from '../data';
import {
  buildUserPayload,
  canManageProtectedAction,
  parseRoleIds,
} from '../form-policy';

const emits = defineEmits(['success']);
const access = useAccessStore();
const canAssign = () => access.accessCodes.includes('system:user:assign-role');
const canListRole = () => access.accessCodes.includes('system:role:list');
const formData = ref<UserRecord>();
const ready = ref(false);
const error = ref('');
const image = ref<PendingImage>();
const preview = ref('');
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
        image.value,
      );
      if (
        formData.value &&
        !canManageProtectedAction(formData.value) &&
        payload.status !== formData.value.status
      )
        throw new Error('唯一超级管理员状态不可修改');
    } catch (cause) {
      error.value = (cause as Error).message;
      return;
    }
    drawerApi.lock();
    try {
      if (id.value) await updateUser(id.value, payload);
      else await createUser(payload as UserCreatePayload);
      message.success('用户已保存');
      emits('success');
      drawerApi.close();
    } catch {
      error.value = '保存失败，表单已保留；头像凭据过期时请重新上传';
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    ready.value = false;
    error.value = '';
    image.value = undefined;
    preview.value = '';
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
      });
      preview.value = current?.avatar ?? '';
      if (!current && canAssign() && canListRole()) {
        const roles = await getAssignableRoleOptions();
        formApi.updateSchema([
          {
            fieldName: 'roleIds',
            componentProps: {
              mode: 'multiple',
              options: roles.map((role) => ({
                label: `${role.name}${role.status === 0 ? '（停用）' : ''}`,
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
      error.value = '读取用户或选项失败，请关闭后重试';
    } finally {
      drawerApi.setState({ loading: false });
    }
  },
});
defineExpose({ drawerApi });
const getDrawerTitle = computed(() =>
  formData.value?.id ? '编辑用户' : '新增用户',
);
async function upload(file: File) {
  if (uploading.value || !ready.value) return false;
  error.value = '';
  if (
    !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
    !file.size ||
    file.size > 2 * 1024 * 1024
  ) {
    error.value = '仅支持不超过2 MiB的 JPEG、PNG、WebP 图片';
    return false;
  }
  const startedAt = Date.now();
  uploading.value = true;
  drawerApi.lock();
  try {
    const result = await uploadUserImage(file);
    image.value = {
      uploadId: result.uploadId,
      expiresAt: startedAt + 2 * 60 * 60 * 1000,
    };
    preview.value = result.url;
  } catch {
    error.value = '头像上传失败，请重新选择图片';
  } finally {
    uploading.value = false;
    drawerApi.unlock();
  }
  return false;
}
</script>
<template>
  <Drawer :title="getDrawerTitle">
    <Form>
      <template #avatar>
        <div class="space-y-2">
          <Spin :spinning="uploading">
            <Upload
              accept="image/jpeg,image/png,image/webp"
              list-type="picture-card"
              :show-upload-list="false"
              :disabled="uploading || !ready"
              :before-upload="upload"
            >
              <img
                v-if="preview"
                :src="preview"
                alt="头像预览"
                class="size-full rounded object-cover"
              />
              <div v-else class="flex flex-col items-center gap-2">
                <Plus class="size-5" />
                <span>上传头像</span>
              </div>
            </Upload>
          </Spin>
          <p class="text-sm text-muted-foreground">
            支持 JPG、PNG、WebP，最大 2 MB
          </p>
          <p v-if="image" class="text-sm text-muted-foreground">
            上传成功，保存用户后生效。
          </p>
        </div>
      </template>
    </Form>
    <p v-if="error" role="alert" class="text-destructive">{{ error }}</p>
  </Drawer>
</template>
