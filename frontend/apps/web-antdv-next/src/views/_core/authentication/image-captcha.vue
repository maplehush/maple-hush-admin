<script setup lang="ts">
import { Input } from 'antdv-next';

import { $t } from '#/locales';
defineProps<{ modelValue?: string; image?: string; busy?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [string]; refresh: [] }>();
</script>
<template>
  <div class="flex w-full items-center gap-3">
    <Input
      class="h-10 min-w-0 flex-1"
      :value="modelValue"
      :placeholder="$t('page.auth.captchaPlaceholder')"
      :aria-label="$t('page.auth.captcha')"
      autocomplete="off"
      :maxlength="4"
      @update:value="emit('update:modelValue', $event)"
    />
    <button
      type="button"
      :aria-label="$t('page.auth.refreshCaptcha')"
      :title="$t('page.auth.refreshCaptcha')"
      :disabled="busy"
      class="h-10 w-32 shrink-0 cursor-pointer overflow-hidden rounded-md border border-input bg-background p-0 transition-colors hover:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-not-allowed disabled:opacity-60"
      @click="emit('refresh')"
    >
      <img
        v-if="image"
        :src="
          image.startsWith('data:') ? image : `data:image/png;base64,${image}`
        "
        :alt="$t('page.auth.captcha')"
        class="h-full w-full select-none object-contain"
      />
      <span v-else>
        {{
          busy ? $t('page.auth.captchaLoading') : $t('page.auth.captchaRetry')
        }}
      </span>
    </button>
  </div>
</template>
