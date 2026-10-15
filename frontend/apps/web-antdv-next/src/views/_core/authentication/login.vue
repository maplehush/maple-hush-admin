<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, markRaw, onActivated, onMounted, ref } from 'vue';

import { AuthenticationLogin, z } from '@vben/common-ui';

import { message } from 'antdv-next';

import { getCaptchaApi } from '#/api';
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
  } catch (error) {
    message.error((error as Error).message);
  } finally {
    busy.value = false;
  }
}
const formSchema = computed((): VbenFormSchema[] => [
  {
    component: 'VbenInput',
    fieldName: 'username',
    label: '用户名',
    componentProps: { placeholder: '请输入用户名', autocomplete: 'username' },
    rules: z.string().min(1, '请输入用户名'),
  },
  {
    component: 'VbenInputPassword',
    fieldName: 'password',
    label: '密码',
    componentProps: {
      placeholder: '请输入密码',
      autocomplete: 'current-password',
    },
    rules: z.string().min(1, '请输入密码'),
  },
  {
    component: markRaw(ImageCaptcha),
    fieldName: 'captchaCode',
    label: '验证码',
    componentProps: {
      image: image.value,
      busy: busy.value,
      onRefresh: refreshCaptcha,
    },
    rules: z.string().min(1, '请输入验证码'),
  },
]);
async function submit(values: Record<string, unknown>) {
  if (!captchaId.value) {
    message.warning('请先获取验证码');
    await refreshCaptcha();
    return;
  }
  try {
    await authStore.authLogin({ ...values, captchaId: captchaId.value });
  } catch (error) {
    message.error((error as Error).message);
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
