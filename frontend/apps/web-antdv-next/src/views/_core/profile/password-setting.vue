<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';

import { computed, ref } from 'vue';

import { ProfilePasswordSetting } from '@vben/common-ui';

import { message } from 'antdv-next';

import { z } from '#/adapter/form';
import { changePasswordApi } from '#/api';
import { useAuthStore } from '#/store';

import { passwordPayload } from './model';

const auth = useAuthStore();
const saving = ref(false);
const error = ref('');

const formSchema = computed((): VbenFormSchema[] => [
  {
    component: 'InputPassword',
    componentProps: { autocomplete: 'current-password', maxlength: 128 },
    fieldName: 'oldPassword',
    label: '旧密码',
    rules: z.string().min(1, '请输入旧密码'),
  },
  {
    component: 'InputPassword',
    componentProps: { autocomplete: 'new-password', maxlength: 128 },
    fieldName: 'newPassword',
    label: '新密码',
    rules: z.string().min(8, '新密码至少 8 个字符').max(128),
  },
  {
    component: 'InputPassword',
    componentProps: { autocomplete: 'new-password', maxlength: 128 },
    fieldName: 'confirmPassword',
    label: '确认新密码',
    dependencies: {
      rules(values) {
        return z.string().refine((value) => value === values.newPassword, {
          message: '两次输入的新密码不一致',
        });
      },
      triggerFields: ['newPassword'],
    },
  },
]);

async function handleSubmit(values: Record<string, unknown>) {
  if (saving.value) {
    return;
  }
  let payload;
  try {
    payload = passwordPayload(values);
  } catch (cause) {
    error.value = (cause as Error).message;
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    await changePasswordApi(payload);
  } catch {
    error.value = '密码修改失败，请核对旧密码后重试';
    saving.value = false;
    return;
  }
  message.success('密码已修改，请使用新密码登录');
  try {
    await auth.completePasswordChange();
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <ProfilePasswordSetting
    class="max-w-xl"
    :form-schema="formSchema"
    @submit="handleSubmit"
  />
  <p v-if="error" role="alert" class="mt-3 text-destructive">{{ error }}</p>
</template>
