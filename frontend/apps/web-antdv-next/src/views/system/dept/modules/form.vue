<script lang="ts" setup>
import type { DeptPayload, DeptRecord } from '#/api/system/dept';

import { computed, nextTick, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import { createDept, getDeptList, updateDept } from '#/api/system/dept';
import { $t } from '#/locales';

import { buildDeptPayload, useSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<DeptRecord>();
const original = ref<DeptPayload>();
const error = ref('');
const ready = ref(false);
type DeptDrawerData = DeptRecord | null | { pid: string };
const getTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', [$t('system.dept.name')])
    : $t('ui.actionTitle.create', [$t('system.dept.name')]),
);
const [Form, formApi] = useVbenForm({
  layout: 'vertical',
  schema: useSchema(),
  showDefaultActions: false,
});
async function resetForm() {
  await formApi.reset();
  if (original.value) await formApi.setValues(original.value);
}
const [Drawer, drawerApi] = useVbenDrawer<DeptDrawerData>({
  async onConfirm() {
    if (!ready.value) return;
    const { valid } = await formApi.validate();
    if (!valid) return;
    error.value = '';
    const values = await formApi.getValues();
    let payload: DeptPayload;
    try {
      payload = buildDeptPayload({
        pid: values.pid ?? '0',
        name: values.name ?? '',
        sort: values.sort,
        status: values.status,
        remark: values.remark ?? '',
      });
    } catch (cause) {
      error.value = (cause as Error).message;
      return;
    }
    drawerApi.lock();
    try {
      if (formData.value?.id) await updateDept(formData.value.id, payload);
      else await createDept(payload);
      message.success($t('system.dept.saved'));
      drawerApi.close();
      emit('success');
    } catch {
      error.value = $t('system.dept.errors.save');
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    error.value = '';
    ready.value = false;
    formData.value = undefined;
    const data = drawerApi.getData();
    drawerApi.setState({ loading: true, showConfirmButton: false });
    try {
      const tree = await getDeptList();
      formData.value = data && 'id' in data ? data : undefined;
      formApi.setState({ schema: useSchema(tree, formData.value?.id) });
      await nextTick();
      original.value = {
        pid: data?.pid ?? '0',
        name: formData.value?.name ?? '',
        sort: formData.value?.sort ?? 0,
        status: formData.value?.status ?? 1,
        remark: formData.value?.remark ?? '',
      };
      await resetForm();
      ready.value = true;
      drawerApi.setState({ showConfirmButton: true });
    } catch {
      error.value = $t('system.dept.errors.treeLoad');
    } finally {
      drawerApi.setState({ loading: false });
    }
  },
});
defineExpose({ drawerApi });
</script>
<template>
  <Drawer :title="getTitle">
    <Form class="mx-4" />
    <p v-if="error" role="alert" class="mx-4 text-destructive">{{ error }}</p>
  </Drawer>
</template>
