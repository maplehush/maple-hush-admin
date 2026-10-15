import type { VxeTableGridColumns } from '@vben/plugins/vxe-table';

import type { VbenFormSchema } from '#/adapter/form';
import type { OnActionClickFn } from '#/adapter/vxe-table';
import type { DeptRecord } from '#/api/system/dept';

import { z } from '#/adapter/form';
import { $t } from '#/locales';

import { parentOptions } from './form-policy';

/**
 * 获取编辑表单的字段配置。如果没有使用多语言，可以直接export一个数组常量
 */
export function useSchema(
  tree: DeptRecord[] = [],
  editId?: string,
): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('system.dept.deptName'),
      rules: z
        .string()
        .trim()
        .min(1, '部门名称必填')
        .max(
          100,
          $t('ui.formRules.maxLength', [$t('system.dept.deptName'), 100]),
        ),
    },
    {
      component: 'TreeSelect',
      componentProps: {
        allowClear: true,
        class: 'w-full',
        treeData: [
          {
            value: '0',
            title: '根部门',
            children: parentOptions(tree, editId),
          },
        ],
      },
      fieldName: 'pid',
      label: $t('system.dept.parentDept'),
    },
    {
      component: 'InputNumber',
      componentProps: { precision: 0, min: -2147483648, max: 2147483647 },
      fieldName: 'sort',
      label: '排序',
      defaultValue: 0,
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
      label: $t('system.dept.status'),
    },
    {
      component: 'Textarea',
      componentProps: {
        maxLength: 500,
        rows: 3,
        showCount: true,
      },
      fieldName: 'remark',
      label: $t('system.dept.remark'),
      rules: z
        .string()
        .max(500, $t('ui.formRules.maxLength', [$t('system.dept.remark'), 500]))
        .optional(),
    },
  ];
}

/**
 * 获取表格列配置
 * @description 使用函数的形式返回列数据而不是直接export一个Array常量，是为了响应语言切换时重新翻译表头
 * @param onActionClick 表格操作按钮点击事件
 */
export function useColumns(
  onActionClick?: OnActionClickFn<DeptRecord>,
  can: (action: string) => boolean = () => false,
): VxeTableGridColumns<DeptRecord> {
  return [
    {
      align: 'left',
      field: 'name',
      fixed: 'left',
      title: $t('system.dept.deptName'),
      treeNode: true,
      width: 150,
    },
    { field: 'sort', title: '排序', width: 100 },
    {
      cellRender: { name: 'CellTag' },
      field: 'status',
      title: $t('system.dept.status'),
      width: 100,
    },
    {
      field: 'createTime',
      title: $t('system.dept.createTime'),
      width: 180,
    },
    {
      field: 'remark',
      title: $t('system.dept.remark'),
    },
    {
      align: 'right',
      cellRender: {
        attrs: {
          nameField: 'name',
          nameTitle: $t('system.dept.name'),
          onClick: onActionClick,
        },
        name: 'CellOperation',
        options: [
          ...(can('create')
            ? [
                {
                  code: 'append',
                  text: '新增下级',
                },
              ]
            : []),
          ...(can('update') ? ['edit' as const] : []),
          ...(can('delete')
            ? [
                {
                  code: 'delete', // 默认的删除按钮
                  disabled: (row: DeptRecord) => {
                    return !!(row.children && row.children.length > 0);
                  },
                },
              ]
            : []),
        ],
      },
      field: 'operation',
      fixed: 'right',
      headerAlign: 'center',
      showOverflow: false,
      title: $t('system.dept.operation'),
      width: 200,
    },
  ];
}
