<script setup lang="ts">
import { ref } from 'vue';

import { Button, InputPassword, message } from 'antdv-next';

import { $t } from '#/locales';
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
    message.warning($t('page.auth.passwordLength'));
    return;
  }
  if (password.value !== confirmation.value) {
    message.warning($t('page.auth.passwordMismatch'));
    return;
  }
  busy.value = true;
  try {
    await auth.changeInitialPassword(password.value);
  } catch {
    message.error($t('page.auth.errors.initialPassword'));
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <form class="flex flex-col gap-5" @submit.prevent="submit">
    <h1 class="text-2xl font-semibold">
      {{ $t('page.auth.initialPassword') }}
    </h1>
    <p class="text-muted-foreground">
      {{ $t('page.auth.initialPasswordDescription') }}
    </p>
    <InputPassword
      v-model:value="password"
      :placeholder="$t('page.auth.newPasswordPlaceholder')"
      :aria-label="$t('page.auth.newPasswordPlaceholder')"
      autocomplete="new-password"
      :maxlength="128"
    />
    <InputPassword
      v-model:value="confirmation"
      :placeholder="$t('page.auth.confirmPasswordPlaceholder')"
      :aria-label="$t('page.auth.confirmPassword')"
      autocomplete="new-password"
      :maxlength="128"
    />
    <Button type="primary" html-type="submit" :loading="busy">
      {{ $t('page.auth.confirmChange') }}
    </Button>
    <RouterLink to="/auth/login">{{ $t('page.auth.backToLogin') }}</RouterLink>
  </form>
</template>
