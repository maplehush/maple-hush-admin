<script setup lang="ts">
import { Button, Input } from 'antdv-next';
defineProps<{ value?: string }>();
const emit = defineEmits<{ 'update:value': [string] }>();
</script>
<template>
  <div class="flex w-full items-center gap-2">
    <input
      type="color"
      aria-label="选择颜色"
      :value="value && /^#[0-9a-f]{6}$/i.test(value) ? value : '#ffffff'"
      @input="emit('update:value', ($event.target as HTMLInputElement).value)"
    /><Input
      :value="value"
      placeholder="#RRGGBB，留空使用普通样式"
      :maxlength="7"
      @update:value="emit('update:value', $event)"
    /><Button @click="emit('update:value', '')">清除</Button>
  </div>
</template>
