<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';

import { computed, ref } from 'vue';

import { ProfilePasswordSetting } from '@vben/common-ui';

import { message } from 'antdv-next';

import { z } from '#/adapter/form';
import { changePasswordApi } from '#/api';
import { $t } from '#/locales';
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
    label: $t('page.profile.oldPassword'),
    rules: z
      .string({ error: $t('page.profile.errors.oldPasswordRequired') })
      .min(1, $t('page.profile.errors.oldPasswordRequired')),
  },
  {
    component: 'InputPassword',
    componentProps: { autocomplete: 'new-password', maxlength: 128 },
    fieldName: 'newPassword',
    label: $t('page.profile.newPassword'),
    rules: z
      .string({ error: $t('page.profile.errors.newPasswordRequired') })
      .min(8, $t('page.profile.newPasswordMinimum'))
      .max(128, $t('page.profile.newPasswordMaximum')),
  },
  {
    component: 'InputPassword',
    componentProps: { autocomplete: 'new-password', maxlength: 128 },
    fieldName: 'confirmPassword',
    label: $t('page.profile.confirmPassword'),
    dependencies: {
      rules(values) {
        return z
          .string({ error: $t('page.profile.errors.confirmPasswordRequired') })
          .min(1, $t('page.profile.errors.confirmPasswordRequired'))
          .refine((value) => value === values.newPassword, {
            message: $t('page.profile.errors.passwordMismatch'),
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
    error.value = $t((cause as Error).message);
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    await changePasswordApi(payload);
  } catch {
    error.value = $t('page.profile.errors.passwordChange');
    saving.value = false;
    return;
  }
  message.success($t('page.profile.errors.passwordChanged'));
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
