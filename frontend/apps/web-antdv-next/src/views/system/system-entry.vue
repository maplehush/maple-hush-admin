<script setup lang="ts">
import { Page } from '@vben/common-ui';
defineProps<{ title: string }>();
</script>
<template>
  <Page :title="title">
    <div class="bg-card rounded-lg p-6">
      <h2 class="mb-3 text-lg">{{ title }}</h2>
      <p>功能尚未开放，真实查询与操作将在 T15 接入。</p>
    </div>
  </Page>
</template>
