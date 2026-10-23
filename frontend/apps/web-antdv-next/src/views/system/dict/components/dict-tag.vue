<script setup lang="ts">
import type { DictOption } from '#/api/system/dict';

import { Tag } from 'antdv-next';

import { colorText } from '../data';
defineProps<{ option: Pick<DictOption, 'color' | 'label'> }>();
</script>
<template>
  <Tag
    :style="
      option.color
        ? {
            backgroundColor: option.color,
            color: colorText(option.color),
            borderColor: option.color,
          }
        : undefined
    "
  >
    {{ option.label }}
  </Tag>
</template>
