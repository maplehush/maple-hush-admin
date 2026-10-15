<script setup lang="ts">
import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';
const user = useUserStore();
</script>
<template>
  <Page title="工作台">
    <div class="bg-card rounded-lg p-6">
      <h2 class="mb-3 text-xl">
        欢迎，{{ user.userInfo?.realName || user.userInfo?.username }}
      </h2>
      <p>请通过左侧菜单进入已获授权的功能。</p>
    </div>
  </Page>
</template>
