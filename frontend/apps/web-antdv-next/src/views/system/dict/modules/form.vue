<script setup lang="ts">
import type { DictItemInput } from '#/api/system/dict';

import { ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { useAccessStore } from '@vben/stores';

import { message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import { dictApi } from '#/api/system/dict';

import { formSchema } from '../data';
import { dictItemPayload } from '../model';
const emit = defineEmits<{ success: [] }>();
const ready = ref(false);
const data = ref<{ kind: 'item' | 'type'; id?: string; dictTypeId?: string }>({
  kind: 'type',
});
const [Form, formApi] = useVbenForm({ schema: [], showDefaultActions: false });
const [Drawer, drawerApi] = useVbenDrawer<{
  kind: 'item' | 'type';
  id?: string;
  dictTypeId?: string;
}>({
  async onOpenChange(open) {
    ready.value = false;
    if (!open) {
      return;
    }
    const incoming = drawerApi.getData();
    if (!incoming) {
      drawerApi.close();
      return;
    }
    data.value = incoming;
    drawerApi.setState({ loading: true, showConfirmButton: false });
    try {
      await formApi.reset();
      formApi.setState({
        schema: formSchema(data.value.kind, !!data.value.id),
      });
      const original = data.value.id
        ? await (data.value.kind === 'type'
            ? dictApi.type(data.value.id)
            : dictApi.item(data.value.id))
        : {};
      await formApi.setValues({
        dictCode: '',
        name: '',
        label: '',
        value: '',
        sort: 0,
        status: 1,
        defaultItem: false,
        remark: '',
        ...original,
        color: ('color' in original ? original.color : null) || '',
        dictTypeId: data.value.dictTypeId,
      });
      ready.value = true;
      drawerApi.setState({ showConfirmButton: true });
    } catch {
      message.error('字典详情加载失败，请关闭后重试');
    } finally {
      drawerApi.setState({ loading: false });
    }
  },
  async onConfirm() {
    if (!ready.value) return;
    const access = useAccessStore();
    if (
      !access.accessCodes.includes(
        `system:dict:${data.value.id ? 'update' : 'create'}`,
      )
    ) {
      return;
    }
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    drawerApi.lock();
    try {
      const values = await formApi.getValues<
        DictItemInput & { dictCode: string; name: string }
      >();
      if (data.value.kind === 'type') {
        await dictApi.saveType(data.value.id, {
          dictCode: values.dictCode.trim(),
          name: values.name.trim(),
          status: values.status,
          remark: values.remark || '',
        });
      } else {
        await dictApi.saveItem(
          data.value.id,
          dictItemPayload({
            ...values,
            dictTypeId: data.value.dictTypeId!,
            sort: values.sort ?? 0,
            color: values.color || null,
          }),
        );
      }
      message.success('保存成功');
      drawerApi.close();
      emit('success');
    } finally {
      drawerApi.unlock();
    }
  },
});
</script>
<template>
  <Drawer
    :title="data.id ? '编辑' : data.kind === 'type' ? '新增类型' : '新增字典项'"
  >
    <Form />
  </Drawer>
</template>
