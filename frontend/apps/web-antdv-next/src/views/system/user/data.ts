import type { DescriptionsItemType } from '@vben/common-ui';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';
import type { UserRecord } from '#/api/system/user';

import { h } from 'vue';

import { Tag } from 'antdv-next';

import { getDeptList } from '#/api/system/dept';
import { $t } from '#/locales';

export function useFormSchema(
  options: {
    editing?: boolean;
    protected?: boolean;
    canAssign?: boolean;
    canListDept?: boolean;
    canListRole?: boolean;
    enableNeedsAssign?: boolean;
  } = {},
): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'username',
      label: '登录账号',
      rules: 'required',
      componentProps: { disabled: options.editing, maxlength: 64 },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('system.user.name'),
      rules: 'required',
      componentProps: { maxlength: 100 },
    },
    {
      component: options.canListDept ? 'ApiTreeSelect' : 'Input',
      componentProps: options.canListDept
        ? {
            allowClear: true,
            api: getDeptList,
            class: 'w-full',
            labelField: 'name',
            valueField: 'id',
            childrenField: 'children',
          }
        : { placeholder: '部门ID；留空不分配，后端校验ID' },
      fieldName: 'deptId',
      label: $t('system.user.dept'),
    },
    ...(!options.editing && options.canAssign
      ? [
          {
            component: options.canListRole ? 'Select' : 'Input',
            componentProps: options.canListRole
              ? { mode: 'multiple', options: [], class: 'w-full' }
              : { placeholder: '角色ID以逗号分隔；后端校验可授予范围' },
            fieldName: 'roleIds',
            label: '角色',
          } as VbenFormSchema,
        ]
      : []),
    {
      component: 'RadioGroup',
      componentProps: {
        disabled: options.protected || options.enableNeedsAssign,
        buttonStyle: 'solid',
        options: [
          { label: $t('common.enabled'), value: 1 },
          { label: $t('common.disabled'), value: 0 },
        ],
        optionType: 'button',
      },
      defaultValue: 1,
      fieldName: 'status',
      label: $t('system.user.status'),
    },
    {
      component: 'Textarea',
      componentProps: { maxlength: 500, rows: 3, showCount: true },
      fieldName: 'remark',
      label: $t('system.user.remark'),
    },
    {
      component: 'Input',
      fieldName: 'avatar',
      label: '头像',
      formItemClass: 'items-start',
    },
  ];
}

export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('system.user.name'),
    },
    {
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: $t('common.enabled'), value: 1 },
          { label: $t('common.disabled'), value: 0 },
        ],
      },
      fieldName: 'status',
      label: $t('system.user.status'),
    },
    {
      component: 'RangePicker',
      fieldName: 'createTime',
      label: $t('system.user.createTime'),
    },
  ];
}

/**
 * 用户详情描述列表项
 * @param row 用户数据
 */
export function useDescriptionItems(row?: UserRecord): DescriptionsItemType[] {
  const enabled = row?.status === 1;
  return [
    { label: '登录账号', content: row?.username },
    { label: $t('system.user.name'), content: row?.name },
    { label: $t('system.user.id'), content: row?.id },
    { label: $t('system.user.dept'), content: row?.deptId ?? '—' },
    { label: '角色ID', content: row?.roleIds?.join('、') },
    { label: '超级管理员', content: row?.superAdmin ? '是' : '否' },
    {
      label: $t('system.user.status'),
      content: () =>
        h(
          Tag,
          {
            color: enabled ? 'success' : 'error',
          },
          {
            default: () =>
              enabled ? $t('common.enabled') : $t('common.disabled'),
          },
        ),
    },
    { label: $t('system.user.createTime'), content: row?.createTime },
    { label: $t('system.user.remark'), content: row?.remark ?? '—' },
  ];
}

export function useColumns(): VxeTableGridColumns<UserRecord> {
  return [
    { field: 'username', title: '登录账号', width: 180 },
    {
      field: 'name',
      title: $t('system.user.name'),
      width: 200,
    },
    {
      field: 'id',
      title: $t('system.user.id'),
      width: 200,
    },
    {
      cellRender: { name: 'CellTag' },
      field: 'status',
      title: $t('system.user.status'),
      width: 100,
    },
    { field: 'deptId', title: '部门ID', width: 160 },
    {
      field: 'roleIds',
      title: '角色ID',
      width: 180,
      formatter: ({ cellValue }) => cellValue?.join('、') || '未分配',
    },
    {
      field: 'remark',
      minWidth: 100,
      title: $t('system.user.remark'),
    },
    {
      field: 'createTime',
      title: $t('system.user.createTime'),
      width: 200,
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      slots: { default: 'action' },
      title: $t('system.user.operation'),
      width: 220,
    },
  ];
}
