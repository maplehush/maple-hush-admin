<script setup lang="ts">
import { ref } from 'vue';

import { Button, InputPassword, message } from 'antdv-next';

import { useAuthStore } from '#/store';
const auth = useAuthStore();
const password = ref('');
const confirmation = ref('');
const busy = ref(false);
async function submit() {
  if (
    password.value.length < 8 ||
    password.value.length > 128 ||
    new TextEncoder().encode(password.value).length > 72
  ) {
    message.warning('新密码需为 8～128 个字符，且 UTF-8 不超过 72 字节');
    return;
  }
  if (password.value !== confirmation.value) {
    message.warning('两次输入的密码不一致');
    return;
  }
  busy.value = true;
  try {
    await auth.changeInitialPassword(password.value);
  } catch (error) {
    message.error((error as Error).message);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <form class="flex flex-col gap-5" @submit.prevent="submit">
    <h1 class="text-2xl font-semibold">首次登录修改密码</h1>
    <p class="text-muted-foreground">
      请设置新密码，完成后使用新密码重新登录。
    </p>
    <InputPassword
      v-model:value="password"
      placeholder="请输入新密码"
      aria-label="新密码"
      autocomplete="new-password"
      :maxlength="128"
    />
    <InputPassword
      v-model:value="confirmation"
      placeholder="请再次输入新密码"
      aria-label="确认新密码"
      autocomplete="new-password"
      :maxlength="128"
    />
    <Button type="primary" html-type="submit" :loading="busy">确认修改</Button>
    <RouterLink to="/auth/login">返回登录</RouterLink>
  </form>
</template>
