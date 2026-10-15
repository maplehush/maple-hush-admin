<script lang="ts" setup>
import type { DeptPayload, DeptRecord } from '#/api/system/dept';

import { computed, nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import { createDept, getDeptList, updateDept } from '#/api/system/dept';
import { $t } from '#/locales';

import { useSchema } from '../data';
import { buildDeptPayload } from '../form-policy';

const emit = defineEmits(['success']);
const formData = ref<DeptRecord>();
const original = ref<DeptPayload>();
const error = ref('');
const ready = ref(false);
type DeptModalData = DeptRecord | null | { pid: string };
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
const [Modal, modalApi] = useVbenModal<DeptModalData>({
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
    modalApi.lock();
    try {
      if (formData.value?.id) await updateDept(formData.value.id, payload);
      else await createDept(payload);
      message.success('部门已保存');
      modalApi.close();
      emit('success');
    } catch {
      error.value = '保存失败，表单已保留，请检查提示后重试';
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    error.value = '';
    ready.value = false;
    formData.value = undefined;
    const data = modalApi.getData();
    modalApi.setState({ loading: true, confirmDisabled: true });
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
      modalApi.setState({ confirmDisabled: false });
    } catch {
      error.value = '读取部门树失败，请关闭后重试';
    } finally {
      modalApi.setState({ loading: false });
    }
  },
});
defineExpose({ modalApi });
</script>
<template>
  <Modal :title="getTitle">
    <Form class="mx-4" />
    <p v-if="error" role="alert" class="mx-4 text-destructive">{{ error }}</p>
    <template #prepend-footer>
      <div class="flex-auto">
        <Button :disabled="!ready" type="primary" danger @click="resetForm">
          {{ $t('common.reset') }}
        </Button>
      </div>
    </template>
  </Modal>
</template>
