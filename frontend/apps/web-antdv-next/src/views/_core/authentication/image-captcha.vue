<script setup lang="ts">
import { Input } from 'antdv-next';
defineProps<{ modelValue?: string; image?: string; busy?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [string]; refresh: [] }>();
</script>
<template>
  <div class="flex w-full items-center gap-3">
    <Input
      :value="modelValue"
      placeholder="请输入验证码"
      aria-label="验证码"
      autocomplete="off"
      :maxlength="6"
      @update:value="emit('update:modelValue', $event)"
    />
    <button
      type="button"
      aria-label="刷新验证码"
      title="点击刷新验证码"
      :disabled="busy"
      class="h-10 min-w-28 overflow-hidden rounded border"
      @click="emit('refresh')"
    >
      <img
        v-if="image"
        :src="
          image.startsWith('data:') ? image : `data:image/png;base64,${image}`
        "
        alt="图形验证码"
        class="h-full w-full object-contain"
      />
      <span v-else>{{ busy ? '加载中' : '点击重试' }}</span>
    </button>
  </div>
</template>
