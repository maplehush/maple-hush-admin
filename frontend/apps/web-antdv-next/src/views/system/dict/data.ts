import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';

import { markRaw } from 'vue';

import { z } from '#/adapter/form';

import ColorInput from './components/color-input.vue';
export const statusOptions = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
];
export function typeSearch(): VbenFormSchema[] {
  return [
    { component: 'Input', fieldName: 'dictCode', label: '字典编码' },
    { component: 'Input', fieldName: 'name', label: '字典名称' },
    {
      component: 'Select',
      fieldName: 'status',
      label: '状态',
      componentProps: { allowClear: true, options: statusOptions },
    },
  ];
}
export function itemSearch(): VbenFormSchema[] {
  return [
    { component: 'Input', fieldName: 'label', label: '标签' },
    {
      component: 'Select',
      fieldName: 'status',
      label: '状态',
      componentProps: { allowClear: true, options: statusOptions },
    },
  ];
}
export function formSchema(
  kind: 'item' | 'type',
  editing: boolean,
): VbenFormSchema[] {
  const fields: VbenFormSchema[] =
    kind === 'type'
      ? [
          {
            component: 'Input',
            fieldName: 'dictCode',
            label: '字典编码',
            componentProps: { disabled: editing, maxlength: 100 },
            rules: z
              .string()
              .regex(
                /^[a-z][a-z0-9_]{0,99}$/,
                '小写字母开头，仅小写字母、数字和下划线，最多100字符',
              ),
          },
          {
            component: 'Input',
            fieldName: 'name',
            label: '类型名称',
            componentProps: { maxlength: 100 },
            rules: z.string().trim().min(1, '请输入名称').max(100),
          },
        ]
      : [
          {
            component: 'Input',
            fieldName: 'label',
            label: '标签',
            componentProps: { maxlength: 200 },
            rules: z.string().trim().min(1, '请输入标签').max(200),
          },
          {
            component: 'Input',
            fieldName: 'value',
            label: '字典值',
            componentProps: { maxlength: 200 },
            rules: z.string().trim().min(1, '请输入字典值').max(200),
          },
          {
            component: markRaw(ColorInput),
            modelPropName: 'value',
            fieldName: 'color',
            label: '颜色（可选）',
            rules: z
              .string()
              .regex(/^$|^#[0-9a-fA-F]{6}$/, '请输入 #RRGGBB 或留空')
              .optional(),
          },
          {
            component: 'InputNumber',
            fieldName: 'sort',
            label: '排序',
            defaultValue: 0,
            componentProps: { precision: 0, min: -2147483648, max: 2147483647 },
          },
          {
            component: 'Checkbox',
            fieldName: 'defaultItem',
            label: '默认项',
            defaultValue: false,
            help: '保存后替换原默认项，与启停状态独立',
          },
        ];
  return [
    ...fields,
    {
      component: 'RadioGroup',
      fieldName: 'status',
      label: '状态',
      defaultValue: 1,
      componentProps: { options: statusOptions, optionType: 'button' },
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
      componentProps: { maxlength: 500, rows: 3 },
      defaultValue: '',
    },
  ];
}
export const typeColumns: VxeTableGridColumns = [
  { field: 'dictCode', title: '字典编码', minWidth: 160 },
  { field: 'name', title: '名称', minWidth: 140 },
  {
    field: 'status',
    title: '状态',
    width: 100,
    cellRender: { name: 'CellTag' },
  },
  { field: 'createTime', title: '创建时间', width: 180 },
  {
    field: 'operation',
    title: '操作',
    width: 260,
    fixed: 'right',
    slots: { default: 'action' },
  },
];
export const itemColumns: VxeTableGridColumns = [
  { field: 'label', title: '标签', minWidth: 140 },
  { field: 'value', title: '值', minWidth: 100 },
  { field: 'color', title: '颜色', width: 160, slots: { default: 'color' } },
  { field: 'sort', title: '排序', width: 90 },
  {
    field: 'defaultItem',
    title: '默认',
    width: 90,
    formatter: ({ cellValue }) => (cellValue ? '是' : '否'),
  },
  {
    field: 'status',
    title: '状态',
    width: 100,
    cellRender: { name: 'CellTag' },
  },
  {
    field: 'operation',
    title: '操作',
    width: 120,
    fixed: 'right',
    slots: { default: 'action' },
  },
];
