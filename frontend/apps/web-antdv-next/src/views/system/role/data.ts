import type { VbenFormSchema } from '#/adapter/form';
import type { OnActionClickFn, VxeTableGridColumns } from '#/adapter/vxe-table';
import type { SystemRole } from '#/api/system/role';

import { useAccessStore } from '@vben/stores';

import { z } from '#/adapter/form';
import { $t } from '#/locales';

export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'roleCode',
      label: '角色编码',
      rules: z
        .string()
        .regex(
          /^[a-z][a-z0-9_]{0,63}$/,
          '小写字母开头，仅含小写字母、数字、下划线，至多64位',
        ),
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('system.role.roleName'),
      rules: z.string().trim().min(1).max(100),
    },
    {
      component: 'RadioGroup',
      componentProps: {
        buttonStyle: 'solid',
        options: [
          { label: $t('common.enabled'), value: 1 },
          { label: $t('common.disabled'), value: 0 },
        ],
        optionType: 'button',
      },
      defaultValue: 1,
      fieldName: 'status',
      label: $t('system.role.status'),
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: $t('system.role.remark'),
      rules: z.string().max(500).optional(),
    },
    {
      component: 'Input',
      fieldName: 'permissions',
      formItemClass: 'items-start',
      label: $t('system.role.setPermissions'),
      modelPropName: 'modelValue',
    },
  ];
}
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('system.role.roleName'),
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
      label: $t('system.role.status'),
    },
    {
      component: 'RangePicker',
      fieldName: 'createTime',
      label: $t('system.role.createTime'),
    },
  ];
}
export function useColumns(
  onActionClick: OnActionClickFn<SystemRole>,
): VxeTableGridColumns<SystemRole> {
  const access = useAccessStore();
  const can = (action: string) =>
    access.accessCodes.includes(`system:role:${action}`);
  return [
    { field: 'name', title: $t('system.role.roleName'), width: 200 },
    { field: 'roleCode', title: '角色编码', width: 180 },
    { field: 'id', title: $t('system.role.id'), width: 100 },
    {
      cellRender: { name: 'CellTag' },
      field: 'status',
      title: $t('system.role.status'),
      width: 100,
    },
    {
      field: 'builtIn',
      title: '内置角色',
      width: 100,
      formatter: ({ row }) => (row.builtIn ? '内置保护' : '否'),
    },
    { field: 'remark', minWidth: 100, title: $t('system.role.remark') },
    { field: 'createTime', title: $t('system.role.createTime'), width: 200 },
    {
      align: 'center',
      cellRender: {
        attrs: {
          nameField: 'name',
          nameTitle: $t('system.role.name'),
          onClick: onActionClick,
        },
        name: 'CellOperation',
        options: [
          {
            code: 'edit',
            show: (row: SystemRole) => !row.builtIn && can('update'),
          },
          {
            code: 'grant',
            text: '菜单权限',
            show: (row: SystemRole) =>
              !row.builtIn &&
              can('update') &&
              can('grant') &&
              access.accessCodes.includes('system:menu:list'),
          },
          {
            code: 'delete',
            show: (row: SystemRole) => !row.builtIn && can('delete'),
          },
        ],
      },
      field: 'operation',
      fixed: 'right',
      title: $t('system.role.operation'),
      width: 230,
    },
  ];
}
