# Add a Frontend Page

This page uses the target application's existing `Page → Grid → Drawer/Form` structure without introducing another Table/Form framework. Create the target application files on your own development branch in the following order.

## 1. Create files

```text
frontend/apps/web-antdv-next/src/
├── api/notice.ts
├── views/notice/
│   ├── list.vue
│   ├── data.ts
│   └── modules/form.vue
└── locales/langs/
    ├── zh-CN/notice.json
    └── en-US/notice.json
```

Use the component names and form schema component values registered in the current `src/adapter/`. For example, the registered plain text multiline input is **Textarea**, rather than an arbitrary spelling such as InputTextArea.

## 2. Define API wrappers and types

File: `src/api/notice.ts`.

```ts
import { requestClient } from '#/api/request';

export interface NoticePayload {
  title: string;
  content: string;
  status: 0 | 1;
}
export interface NoticeRecord extends NoticePayload {
  id: string;
  createTime: string;
  updateTime: string;
}
export interface NoticePage {
  items: NoticeRecord[];
  total: number;
}
export interface NoticeQuery {
  page: number;
  pageSize: number;
  title?: string;
  status?: 0 | 1;
}
export const getNoticeList = (params: NoticeQuery) =>
  requestClient.get<NoticePage>('/notice/list', { params });
export const getNotice = (id: string) =>
  requestClient.get<NoticeRecord>(`/notice/${id}`);
export const createNotice = (data: NoticePayload) =>
  requestClient.post<{ id: string }>('/notice', data);
export const updateNotice = (id: string, data: NoticePayload) =>
  requestClient.put(`/notice/${id}`, data);
export const deleteNotice = (id: string) =>
  requestClient.delete(`/notice/${id}`);
```

`requestClient` already configures `/api` and response unwrapping, so use `/notice` here without repeating `/api/notice`. Do not use a separate fetch implementation that bypasses shared authentication. Keep IDs as strings throughout; do not call Number(id).

Import these functions directly from `#/api/notice`. If your team uses a shared barrel export, update `src/api/index.ts` as well; new files are not automatically exported.

## 3. Add locale entries

The filename becomes the namespace: for example, use title from `notice.json` as `$t('notice.title')`. The current application supports Simplified Chinese and English. Keep the same keys in both files. The zh-CN example below intentionally contains Chinese translations.

::: code-group

```json [zh-CN/notice.json]
{
  "title": "公告标题",
  "content": "公告内容",
  "status": "状态",
  "enabled": "启用",
  "disabled": "停用",
  "list": "公告列表",
  "create": "新增公告",
  "edit": "编辑公告",
  "created": "公告已创建",
  "updated": "公告已更新",
  "deleted": "公告已删除",
  "noList": "当前账号没有公告查询权限",
  "operation": "操作",
  "createTime": "创建时间",
  "titleRule": "请输入 1～200 字符的标题",
  "contentRule": "请输入 1～5000 字符的纯文本内容",
  "statusRule": "请选择状态"
}
```

```json [en-US/notice.json]
{
  "title": "Title",
  "content": "Content",
  "status": "Status",
  "enabled": "Enabled",
  "disabled": "Disabled",
  "list": "Announcements",
  "create": "Create announcement",
  "edit": "Edit announcement",
  "created": "Announcement created",
  "updated": "Announcement updated",
  "deleted": "Announcement deleted",
  "noList": "You do not have announcement query permission",
  "operation": "Actions",
  "createTime": "Created at",
  "titleRule": "Enter a title of 1 to 200 characters",
  "contentRule": "Enter 1 to 5000 characters of plain text",
  "statusRule": "Select a status"
}
```

:::

## 4. Centralize table and form configuration

File: `src/views/notice/data.ts`.

```ts
import type { VxeTableGridColumns } from '@vben/plugins/vxe-table';
import type { VbenFormSchema } from '#/adapter/form';
import type { OnActionClickFn } from '#/adapter/vxe-table';
import type { NoticeRecord } from '#/api/notice';

import { z } from '#/adapter/form';
import { $t } from '#/locales';

export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input', fieldName: 'title', label: $t('notice.title'),
      rules: z.string().trim().min(1, $t('notice.titleRule'))
        .max(200, $t('notice.titleRule')),
    },
    {
      component: 'Textarea', fieldName: 'content', label: $t('notice.content'),
      componentProps: { rows: 6, maxlength: 5000 },
      rules: z.string().trim().min(1, $t('notice.contentRule'))
        .max(5000, $t('notice.contentRule')),
    },
    {
      component: 'RadioGroup', fieldName: 'status', label: $t('notice.status'),
      defaultValue: 1,
      componentProps: {
        options: [
          { label: $t('notice.enabled'), value: 1 },
          { label: $t('notice.disabled'), value: 0 },
        ],
      },
      rules: z.union([z.literal(0), z.literal(1)], { error: $t('notice.statusRule') }),
    },
  ];
}

export function useSearchSchema(): VbenFormSchema[] {
  return [{
    component: 'Input', fieldName: 'title', label: $t('notice.title'),
    componentProps: { allowClear: true, maxlength: 200 },
  }];
}

export function useColumns(
  onClick: OnActionClickFn<NoticeRecord>,
  can: (action: string) => boolean,
): VxeTableGridColumns {
  return [
    { field: 'title', title: $t('notice.title'), minWidth: 200 },
    {
      field: 'status', title: $t('notice.status'), width: 100,
      formatter: ({ cellValue }) =>
        $t(cellValue === 1 ? 'notice.enabled' : 'notice.disabled'),
    },
    { field: 'createTime', title: $t('notice.createTime'), width: 180 },
    {
      field: 'operation', title: $t('notice.operation'), width: 160, fixed: 'right',
      cellRender: {
        name: 'CellOperation',
        attrs: { nameField: 'title', nameTitle: $t('notice.title'), onClick },
        options: [
          { code: 'edit', show: () => can('update') },
          { code: 'delete', show: () => can('delete') },
        ],
      },
    },
  ];
}
```

The current adapter registers `CellOperation`, which reuses VbenTableAction and includes the existing edit/delete controls and deletion confirmation. Do not add a duplicate confirmation dialog. Business callbacks must still check permissions, while backend API permissions provide the final enforcement.

Frontend rules improve the user experience; backend DTOs and the business layer still perform validation. The example list provides title filtering only. The API already supports status; add a status control to the search schema if needed.

## 5. Connect the list page to Grid and the drawer

File: `src/views/notice/list.vue`.

```vue
<script setup lang="ts">
import type { OnActionClickParams, VxeTableGridOptions } from '#/adapter/vxe-table';
import type { NoticeRecord } from '#/api/notice';

import { watch } from 'vue';
import { Page, useVbenDrawer } from '@vben/common-ui';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';
import { Alert, Button, message } from 'antdv-next';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteNotice, getNoticeList } from '#/api/notice';
import { $t } from '#/locales';
import { useColumns, useSearchSchema } from './data';
import Form from './modules/form.vue';

const access = useAccessStore();
const can = (action: string) => access.accessCodes.includes(`system:notice:${action}`);
const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form, destroyOnClose: true,
});
const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: { schema: useSearchSchema(), submitOnChange: true },
  gridOptions: {
    columns: useColumns(onActionClick, can), height: 'auto', keepSource: true,
    rowConfig: { keyField: 'id' },
    proxyConfig: {
      ajax: {
        query: async ({ page }, values: { title?: string }) => {
          if (!can('list')) return { items: [], total: 0 };
          return getNoticeList({
            page: page.currentPage, pageSize: page.pageSize,
            title: values.title?.trim() || undefined,
          });
        },
      },
    },
    toolbarConfig: { refresh: true, search: true },
  } as VxeTableGridOptions<NoticeRecord>,
});
watch(() => preferences.app.locale, () => {
  gridApi.setGridOptions({ columns: useColumns(onActionClick, can) });
  gridApi.formApi.setState({ schema: useSearchSchema() });
});

function onRefresh() {
  void gridApi.query();
}
function onCreate() {
  if (can('create')) formDrawerApi.setData({}).open();
}
async function onActionClick({ code, row }: OnActionClickParams<NoticeRecord>) {
  if (code === 'edit' && can('update')) {
    formDrawerApi.setData({ id: row.id }).open();
  }
  if (code === 'delete' && can('delete')) {
    try {
      await deleteNotice(row.id);
      message.success($t('notice.deleted'));
      onRefresh();
    } catch {
      // requestClient has shown the error; keep the current list for retry.
      return;
    }
  }
}
</script>

<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <Alert v-if="!can('list')" :message="$t('notice.noList')" type="warning" />
    <Grid v-else :table-title="$t('notice.list')">
      <template #toolbar-tools>
        <Button v-if="can('create')" type="primary" @click="onCreate">
          {{ $t('notice.create') }}
        </Button>
      </template>
    </Grid>
  </Page>
</template>
```

The pagination adapter already maps result to items and total to total. Do not replace them with a records field the backend does not provide. Grid performs the query after search criteria are submitted; do not maintain a separate pagination state.

## 6. Share a form for creating and editing

File: `src/views/notice/modules/form.vue`.

```vue
<script setup lang="ts">
import type { NoticePayload } from '#/api/notice';
import { computed, ref, watch } from 'vue';
import { useVbenDrawer } from '@vben/common-ui';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';
import { message } from 'antdv-next';
import { useVbenForm } from '#/adapter/form';
import { createNotice, getNotice, updateNotice } from '#/api/notice';
import { $t } from '#/locales';
import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const access = useAccessStore();
const id = ref<string>();
const ready = ref(false);
const can = (action: string) => access.accessCodes.includes(`system:notice:${action}`);
const title = computed(() => $t(id.value ? 'notice.edit' : 'notice.create'));
const [Form, formApi] = useVbenForm({
  schema: useFormSchema(), showDefaultActions: false,
});
watch(() => preferences.app.locale, () => {
  formApi.setState({ schema: useFormSchema() });
});
const [Drawer, drawerApi] = useVbenDrawer<{ id?: string }>({
  async onOpenChange(open) {
    ready.value = false;
    if (!open) return;
    id.value = drawerApi.getData()?.id;
    drawerApi.setState({ loading: true, showConfirmButton: false });
    try {
      await formApi.reset();
      if (!can(id.value ? 'update' : 'create')) {
        drawerApi.close();
        return;
      }
      const values = id.value
        ? await getNotice(id.value)
        : { title: '', content: '', status: 1 };
      await formApi.setValues(values);
      ready.value = true;
      drawerApi.setState({ showConfirmButton: true });
    } catch {
      // The request layer has shown the error; do not allow saving after loading fails.
      drawerApi.close();
    } finally {
      drawerApi.setState({ loading: false });
    }
  },
  async onConfirm() {
    if (!ready.value || !can(id.value ? 'update' : 'create')) return;
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues<NoticePayload>();
    const payload: NoticePayload = {
      title: values.title.trim(), content: values.content.trim(), status: values.status,
    };
    drawerApi.lock();
    try {
      if (id.value) await updateNotice(id.value, payload);
      else await createNotice(payload);
      message.success($t(id.value ? 'notice.updated' : 'notice.created'));
      drawerApi.close();
      emit('success');
    } finally {
      drawerApi.unlock();
    }
  },
});
defineExpose({ drawerApi });
</script>

<template>
  <Drawer :title="title"><Form /></Drawer>
</template>
```

Reset the form each time the drawer opens and fetch details again by ID when editing. If loading fails, do not allow an empty form to overwrite existing data. Construct only the DTO's three fields for submission rather than sending the whole record with its id, timestamps, or temporary list fields.

Tables and forms use components through application adapters. Existing mechanisms handle confirmation requests, button locking, and error messages. Use `{{ content }}` for a content preview rather than rendering plain text with v-html.

Creating the page files does not automatically add them to the left navigation. Continue to [Menus and Permissions](./permissions.md).
