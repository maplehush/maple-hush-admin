<script setup lang="ts">
import type { UploadFile, UploadProps } from 'antdv-next';

import type { VbenFormSchema } from '#/adapter/form';

import { computed, nextTick, onMounted, ref } from 'vue';

import { ProfileBaseSetting } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { message } from 'antdv-next';

import { z } from '#/adapter/form';
import { getCurrentUserApi, updateAvatarApi, updateProfileApi } from '#/api';
import { session } from '#/auth/session';

import { profilePayload } from './model';

const userStore = useUserStore();
const profileBaseSettingRef = ref<InstanceType<typeof ProfileBaseSetting>>();
const loading = ref(true);
const saving = ref(false);
const uploading = ref(false);
const error = ref('');
const avatar = ref<null | string>(null);

function avatarFiles(): UploadFile[] {
  return avatar.value
    ? [
        {
          uid: 'current-avatar',
          name: '当前头像',
          status: 'done',
          url: avatar.value,
        },
      ]
    : [];
}

async function restoreAvatar() {
  await nextTick();
  await profileBaseSettingRef.value?.getFormApi().setValues({
    avatar: avatarFiles(),
  });
}

function beforeUpload(file: File) {
  if (
    !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
    !file.size ||
    file.size > 2 * 1024 * 1024
  ) {
    error.value = '仅支持不超过 2 MB 的 JPG、PNG、WebP 图片';
    Object.assign(file, { status: 'removed' });
    void restoreAvatar();
    return false;
  }
  return true;
}

async function uploadAvatar({
  file,
  onError,
  onProgress,
  onSuccess,
}: Parameters<NonNullable<UploadProps['customRequest']>>[0]) {
  if (loading.value || uploading.value || !(file instanceof File)) {
    onError?.(new Error('暂时无法上传，请稍后重试'));
    return;
  }
  error.value = '';
  uploading.value = true;
  const epoch = session.state.epoch;
  try {
    onProgress?.({ percent: 0 });
    const result = await updateAvatarApi(file);
    if (epoch !== session.state.epoch) {
      throw new Error('登录状态已变化');
    }
    avatar.value = result.avatar;
    Object.assign(file, { url: result.avatar });
    onProgress?.({ percent: 100 });
    onSuccess?.(result);
    if (userStore.userInfo) {
      userStore.setUserInfo({ ...userStore.userInfo, avatar: result.avatar });
    }
    await restoreAvatar();
    message.success('头像已更新');
  } catch (cause) {
    error.value = '头像上传失败，请重新选择图片';
    onError?.(cause instanceof Error ? cause : new Error(error.value));
    await restoreAvatar();
  } finally {
    uploading.value = false;
  }
}

const formSchema = computed((): VbenFormSchema[] => [
  {
    component: 'Input',
    componentProps: { disabled: true },
    fieldName: 'username',
    label: '登录账号',
  },
  {
    component: 'Input',
    componentProps: { disabled: true },
    fieldName: 'roles',
    label: '角色',
  },
  {
    component: 'Input',
    fieldName: 'realName',
    label: '姓名',
    rules: z
      .string()
      .trim()
      .min(1, '请输入姓名')
      .max(100, '姓名最多 100 个字符'),
  },
  {
    component: 'Textarea',
    componentProps: { maxlength: 500, rows: 4, showCount: true },
    fieldName: 'introduction',
    label: '个人简介',
  },
  {
    component: 'Upload',
    componentProps: () => ({
      accept: '.jpg,.jpeg,.png,.webp',
      beforeUpload,
      customRequest: uploadAvatar,
      disabled: loading.value || uploading.value,
      listType: 'picture-card',
      maxCount: 1,
      maxSize: 2,
      multiple: false,
      showUploadList: { showRemoveIcon: false },
    }),
    fieldName: 'avatar',
    label: '头像',
    help: '支持 JPG、PNG、WebP，最大 2 MB；选择后立即更新',
    renderComponentContent: () => ({ default: () => '上传头像' }),
  },
]);

async function loadProfile() {
  loading.value = true;
  error.value = '';
  const epoch = session.state.epoch;
  try {
    const data = await getCurrentUserApi();
    if (epoch !== session.state.epoch) {
      return;
    }
    avatar.value = data.avatar;
    await profileBaseSettingRef.value?.getFormApi().setValues({
      username: data.username,
      roles: data.roles.join('、'),
      realName: data.realName,
      introduction: data.introduction ?? '',
      avatar: avatarFiles(),
    });
  } catch {
    error.value = '个人资料加载失败，请刷新页面重试';
  } finally {
    loading.value = false;
  }
}

async function handleSubmit(values: Record<string, unknown>) {
  if (loading.value || saving.value || uploading.value) {
    return;
  }
  let payload;
  try {
    payload = profilePayload(values);
  } catch (cause) {
    error.value = (cause as Error).message;
    return;
  }
  saving.value = true;
  error.value = '';
  const epoch = session.state.epoch;
  try {
    const result = await updateProfileApi(payload);
    if (epoch !== session.state.epoch) {
      return;
    }
    if (userStore.userInfo) {
      userStore.setUserInfo({
        ...userStore.userInfo,
        realName: result.realName,
        introduction: result.introduction ?? '',
      });
    }
    message.success('个人资料已保存');
  } catch {
    error.value = '保存失败，请重试';
  } finally {
    saving.value = false;
  }
}

onMounted(loadProfile);
</script>

<template>
  <ProfileBaseSetting
    ref="profileBaseSettingRef"
    :form-schema="formSchema"
    @submit="handleSubmit"
  />
  <p v-if="error" role="alert" class="mt-3 text-destructive">{{ error }}</p>
</template>
