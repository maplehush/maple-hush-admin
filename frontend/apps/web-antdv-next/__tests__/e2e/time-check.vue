<script setup lang="ts">
import { ref } from 'vue';

import { DatePicker } from 'antdv-next';

import {
  parseServerDateTime,
  SERVER_DATE_TIME_FORMAT,
} from '../../src/utils/date-time';
const value = ref(parseServerDateTime('2026-10-14 10:20:30'));
</script>
<template>
  <DatePicker
    v-model:value="value"
    show-time
    :format="SERVER_DATE_TIME_FORMAT"
  /><output>{{ value?.format(SERVER_DATE_TIME_FORMAT) }}</output>
</template>
