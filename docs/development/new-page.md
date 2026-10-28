# 新增前端页面

本页沿用目标应用已有的 `Page → Grid → Drawer/Form` 结构，不另建一套 Table/Form 基础框架。按以下顺序在自己的开发分支创建目标应用文件。

## 1. 创建文件

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

组件名、form schema 的 component 值以当前 `src/adapter/` 为准。例如纯文本多行输入注册名是 **Textarea**，不是任意拼写的 InputTextArea。

## 2. 封装接口与类型

文件：`src/api/notice.ts`。

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

`requestClient` 已配置 `/api` 和响应拆包，所以这里写 `/notice`，不重复写 `/api/notice`，不使用另一套 fetch 来绕开统一认证。ID 始终保留 string，不调用 Number(id)。

这些函数可以直接从 `#/api/notice` 导入；若团队采用统一 barrel 导出，再同步 `src/api/index.ts`，不要假设新文件会自动导出。

## 3. 添加语言词条

文件名会成为命名空间，例如 `notice.json` 的 title 通过 `$t('notice.title')` 使用。当前应用实际支持简体中文和英文，两份文件保持同样的键。

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

## 4. 集中维护表格与表单配置

文件：`src/views/notice/data.ts`。

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

`CellOperation` 由当前适配器注册并复用 VbenTableAction，包含现有编辑、删除操作展示与删除确认；不要再叠加一套重复确认框。业务回调仍要检查权限，后端接口权限才是最终约束。

前端规则用于交互体验，后端 DTO 和业务层仍负责校验。示例只在列表提供标题筛选；状态已在接口中支持，需要时再给搜索 schema 添加状态控件。

## 5. 列表页接入 Grid 与抽屉

文件：`src/views/notice/list.vue`。

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
      // requestClient 已显示错误，保留当前列表供用户重试。
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

分页适配器已配置 result 为 items、total 为 total，不改成后端没有提供的 records 字段。提交搜索条件后由 Grid 发起查询，不手写第二套分页状态。

## 6. 新增与编辑共用表单

文件：`src/views/notice/modules/form.vue`。

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
      // 请求层已统一提示，加载失败时不开放保存。
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

抽屉每次打开重置表单，编辑时按 ID 重新读取详情；加载失败不能提交空表单覆盖已有数据。提交时只构造 DTO 中的三个字段，不把 id、时间、列表临时字段整体传给后端。

表格与表单通过应用适配器使用组件；确认请求、锁定按钮和错误提示由已有机制衔接。需要显示正文预览时使用 `{{ content }}`，不要将纯文本切换成 v-html。

页面文件写好后还不会自动出现在左侧菜单，继续[菜单与权限](./permissions.md)。
