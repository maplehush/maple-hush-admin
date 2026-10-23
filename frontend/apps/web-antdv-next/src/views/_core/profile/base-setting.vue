<script setup lang="ts">
import type { UploadFile, UploadProps } from 'antdv-next';

import type { VbenFormSchema } from '#/adapter/form';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { ProfileBaseSetting } from '@vben/common-ui';
import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import { message } from 'antdv-next';

import { z } from '#/adapter/form';
import { getCurrentUserApi, updateAvatarApi, updateProfileApi } from '#/api';
import { session } from '#/auth/session';
import { $t } from '#/locales';

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
          name: $t('page.profile.avatarName'),
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
    error.value = $t('page.profile.avatarFilesHelp');
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
    onError?.(new Error($t('page.profile.errors.avatarUnavailable')));
    return;
  }
  error.value = '';
  uploading.value = true;
  const epoch = session.state.epoch;
  try {
    onProgress?.({ percent: 0 });
    const result = await updateAvatarApi(file);
    if (epoch !== session.state.epoch) {
      throw new Error($t('page.profile.errors.avatarUnavailable'));
    }
    avatar.value = result.avatar;
    Object.assign(file, { url: result.avatar });
    onProgress?.({ percent: 100 });
    onSuccess?.(result);
    if (userStore.userInfo) {
      userStore.setUserInfo({ ...userStore.userInfo, avatar: result.avatar });
    }
    await restoreAvatar();
    message.success($t('page.profile.errors.avatarChanged'));
  } catch (cause) {
    error.value = $t('page.profile.errors.avatarUpload');
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
    label: $t('page.profile.username'),
  },
  {
    component: 'Input',
    componentProps: { disabled: true },
    fieldName: 'roles',
    label: $t('page.profile.roles'),
  },
  {
    component: 'Input',
    fieldName: 'realName',
    label: $t('page.profile.name'),
    rules: z
      .string({ error: $t('page.profile.errors.nameRequired') })
      .trim()
      .min(1, $t('page.profile.errors.nameRequired'))
      .max(100, $t('page.profile.errors.nameMax')),
  },
  {
    component: 'Textarea',
    componentProps: { maxlength: 500, rows: 4, showCount: true },
    fieldName: 'introduction',
    label: $t('page.profile.introduction'),
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
    label: $t('page.profile.avatar'),
    help: $t('page.profile.avatarHelp'),
    renderComponentContent: () => ({
      default: () => $t('page.profile.uploadAvatar'),
    }),
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
      roles: data.roles.join(preferences.app.locale === 'en-US' ? ', ' : '、'),
      realName: data.realName,
      introduction: data.introduction ?? '',
      avatar: avatarFiles(),
    });
  } catch {
    error.value = $t('page.profile.errors.profileLoad');
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
    error.value = $t((cause as Error).message);
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
    message.success($t('page.profile.errors.profileSaved'));
  } catch {
    error.value = $t('page.profile.errors.profileSave');
  } finally {
    saving.value = false;
  }
}

onMounted(loadProfile);
watch(
  () => preferences.app.locale,
  () => void restoreAvatar(),
);
</script>

<template>
  <ProfileBaseSetting
    ref="profileBaseSettingRef"
    :form-schema="formSchema"
    @submit="handleSubmit"
  />
  <p v-if="error" role="alert" class="mt-3 text-destructive">{{ error }}</p>
</template>
