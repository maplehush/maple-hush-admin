<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, markRaw, onActivated, onMounted, ref } from 'vue';

import { AuthenticationLogin, z } from '@vben/common-ui';

import { message } from 'antdv-next';

import { getCaptchaApi } from '#/api';
import { $t } from '#/locales';
import { useAuthStore } from '#/store';

import ImageCaptcha from './image-captcha.vue';
defineOptions({ name: 'Login' });
const authStore = useAuthStore();
const login = ref<InstanceType<typeof AuthenticationLogin>>();
const captchaId = ref('');
const image = ref('');
const busy = ref(false);
async function refreshCaptcha() {
  if (busy.value) {
    return;
  }
  busy.value = true;
  captchaId.value = '';
  image.value = '';
  login.value?.getFormApi().setFieldValue('captchaCode', '');
  try {
    const data = await getCaptchaApi();
    captchaId.value = data.captchaId;
    image.value = data.imageBase64;
  } catch {
    message.error($t('page.auth.errors.captchaLoad'));
  } finally {
    busy.value = false;
  }
}
const formSchema = computed((): VbenFormSchema[] => [
  {
    component: 'VbenInput',
    fieldName: 'username',
    label: $t('page.auth.username'),
    componentProps: {
      placeholder: $t('page.auth.usernamePlaceholder'),
      autocomplete: 'username',
    },
    rules: z
      .string({ error: $t('page.auth.usernamePlaceholder') })
      .min(1, $t('page.auth.usernamePlaceholder')),
  },
  {
    component: 'VbenInputPassword',
    fieldName: 'password',
    label: $t('page.auth.password'),
    componentProps: {
      placeholder: $t('page.auth.passwordPlaceholder'),
      autocomplete: 'current-password',
    },
    rules: z
      .string({ error: $t('page.auth.passwordPlaceholder') })
      .min(1, $t('page.auth.passwordPlaceholder')),
  },
  {
    component: markRaw(ImageCaptcha),
    fieldName: 'captchaCode',
    label: $t('page.auth.captcha'),
    componentProps: {
      image: image.value,
      busy: busy.value,
      onRefresh: refreshCaptcha,
    },
    rules: z
      .string({ error: $t('page.auth.captchaPlaceholder') })
      .min(1, $t('page.auth.captchaPlaceholder')),
  },
]);
async function submit(values: Record<string, unknown>) {
  if (!captchaId.value) {
    message.warning($t('page.auth.getCaptchaFirst'));
    await refreshCaptcha();
    return;
  }
  try {
    await authStore.authLogin({ ...values, captchaId: captchaId.value });
  } catch {
    message.error($t('page.auth.errors.login'));
    await refreshCaptcha();
  }
}
onMounted(refreshCaptcha);
// Vben 登录布局缓存 Login；从首次改密页回来时旧验证码已消费，必须重取。
onActivated(() => {
  login.value?.getFormApi().setFieldValue('password', '');
  void refreshCaptcha();
});
</script>
<template>
  <AuthenticationLogin
    ref="login"
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    :show-code-login="false"
    :show-qrcode-login="false"
    :show-register="false"
    :show-forget-password="false"
    :show-third-party-login="false"
    :show-remember-me="false"
    @submit="submit"
  />
</template>
