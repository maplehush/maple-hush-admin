import type { Color } from 'antdv-next/dist/color-picker';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';
import type { DictItem, DictItemInput, DictType } from '#/api/system/dict';

import { useAccessStore } from '@vben/stores';

import { AggregationColor } from 'antdv-next/dist/color-picker/color';

import { z } from '#/adapter/form';
import { $t } from '#/locales';

/**
 * 统一生成状态字段选项，避免不同表单使用不一致的取值。
 */
const statusOptions = () => [
  { label: $t('common.enabled'), value: 1 },
  { label: $t('common.disabled'), value: 0 },
];

/**
 * 构造字典类型列表的筛选项。
 */
export function typeSearch(): VbenFormSchema[] {
  return [
    { component: 'Input', fieldName: 'name', label: $t('system.dict.name') },
    {
      component: 'Input',
      fieldName: 'dictCode',
      label: $t('system.dict.code'),
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: $t('system.dict.status'),
      componentProps: { allowClear: true, options: statusOptions() },
    },
  ];
}

/**
 * 构造字典项列表的筛选项。
 */
export function itemSearch(): VbenFormSchema[] {
  return [
    { component: 'Input', fieldName: 'label', label: $t('system.dict.label') },
    {
      component: 'Select',
      fieldName: 'status',
      label: $t('system.dict.status'),
      componentProps: { allowClear: true, options: statusOptions() },
    },
  ];
}

/**
 * 根据类型或字典项场景生成新增、编辑表单字段。
 */
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
            label: $t('system.dict.code'),
            componentProps: { disabled: editing, maxlength: 100 },
            rules: z
              .string()
              .regex(
                /^[a-z][a-z0-9_]{0,99}$/,
                $t('system.dict.errors.codeFormat'),
              ),
          },
          {
            component: 'Input',
            fieldName: 'name',
            label: $t('system.dict.name'),
            componentProps: { maxlength: 100 },
            rules: z
              .string()
              .trim()
              .min(1, $t('system.dict.errors.nameRequired'))
              .max(100),
          },
        ]
      : [
          {
            component: 'Input',
            fieldName: 'label',
            label: $t('system.dict.label'),
            componentProps: { maxlength: 200 },
            rules: z
              .string()
              .trim()
              .min(1, $t('system.dict.errors.labelRequired'))
              .max(200),
          },
          {
            component: 'Input',
            fieldName: 'value',
            label: $t('system.dict.value'),
            componentProps: { maxlength: 200 },
            rules: z
              .string()
              .trim()
              .min(1, $t('system.dict.errors.valueRequired'))
              .max(200),
          },
          {
            component: 'ColorPicker',
            // 字典仅存六位 HEX 字符串；清除时返回空串，提交时转换为 null。
            componentProps: {
              allowClear: true,
              disabledAlpha: true,
              disabledFormat: true,
              format: 'hex',
              mode: 'single',
              presets: [
                {
                  label: $t('system.dict.commonColors'),
                  defaultOpen: true,
                  colors: [
                    '#F5222D',
                    '#FA541C',
                    '#FA8C16',
                    '#FAAD14',
                    '#FADB14',
                    '#A0D911',
                    '#52C41A',
                    '#13C2C2',
                    '#1677FF',
                    '#2F54EB',
                    '#722ED1',
                    '#EB2F96',
                    '#000000',
                    '#595959',
                    '#8C8C8C',
                    '#BFBFBF',
                    '#D9D9D9',
                    '#F0F0F0',
                    '#FFFFFF',
                  ].map((color) => new AggregationColor(color)),
                },
              ],
              showText: true,
              valueFormat: (color: Color) =>
                color.cleared ? '' : color.toHexString(),
            },
            fieldName: 'color',
            label: $t('system.dict.color'),
            help: $t('system.dict.colorValueHelp'),
            rules: z
              .string()
              .regex(
                /^$|^#[0-9a-fA-F]{6}$/,
                $t('system.dict.errors.colorFormat'),
              )
              .optional(),
          },
          {
            component: 'InputNumber',
            fieldName: 'sort',
            label: $t('system.dict.sort'),
            defaultValue: 0,
            componentProps: { precision: 0, min: -2147483648, max: 2147483647 },
          },
          {
            component: 'Checkbox',
            fieldName: 'defaultItem',
            label: $t('system.dict.defaultItem'),
            defaultValue: false,
            help: $t('system.dict.defaultItemHelp'),
          },
        ];
  return [
    ...fields,
    {
      component: 'RadioGroup',
      fieldName: 'status',
      label: $t('system.dict.status'),
      defaultValue: 1,
      componentProps: {
        buttonStyle: 'solid',
        options: statusOptions(),
        optionType: 'button',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: $t('system.dict.remark'),
      componentProps: { maxlength: 500, rows: 3 },
      defaultValue: '',
    },
  ];
}

/**
 * 构造字典类型表格列，并按当前权限控制状态开关。
 */
export function typeColumns(
  onStatusChange: (status: 0 | 1, row: DictType) => Promise<boolean>,
): VxeTableGridColumns {
  const access = useAccessStore();
  return [
    { field: 'name', title: $t('system.dict.name'), minWidth: 140 },
    { field: 'dictCode', title: $t('system.dict.code'), minWidth: 160 },
    {
      field: 'status',
      title: $t('system.dict.status'),
      width: 100,
      cellRender: {
        attrs: {
          beforeChange: onStatusChange,
          isDisabled: () => !access.accessCodes.includes('system:dict:update'),
        },
        name: 'CellSwitch',
      },
    },
    { field: 'remark', title: $t('system.dict.remark'), minWidth: 180 },
    { field: 'createTime', title: $t('system.dict.createTime'), width: 180 },
    {
      field: 'operation',
      title: $t('system.dict.operation'),
      width: 260,
      fixed: 'right',
      slots: { default: 'action' },
    },
  ];
}

/**
 * 构造字典项表格列，并按当前权限控制状态开关。
 */
export function itemColumns(
  onStatusChange: (status: 0 | 1, row: DictItem) => Promise<boolean>,
): VxeTableGridColumns {
  const access = useAccessStore();
  return [
    { field: 'label', title: $t('system.dict.label'), minWidth: 140 },
    { field: 'value', title: $t('system.dict.value'), minWidth: 100 },
    {
      field: 'color',
      title: $t('system.dict.color'),
      width: 140,
      slots: { default: 'color' },
    },
    { field: 'sort', title: $t('system.dict.sort'), width: 90 },
    {
      field: 'defaultItem',
      title: $t('system.dict.defaultItem'),
      width: 90,
      formatter: ({ cellValue }) =>
        cellValue ? $t('common.yes') : $t('common.no'),
    },
    {
      field: 'status',
      title: $t('system.dict.status'),
      width: 100,
      cellRender: {
        attrs: {
          beforeChange: onStatusChange,
          isDisabled: () => !access.accessCodes.includes('system:dict:update'),
        },
        name: 'CellSwitch',
      },
    },
    {
      field: 'operation',
      title: $t('system.dict.operation'),
      width: 120,
      fixed: 'right',
      slots: { default: 'action' },
    },
  ];
}

/**
 * 字典项提交数据清理及标签文字颜色计算。
 */
export function dictItemPayload(form: DictItemInput): DictItemInput {
  const color = form.color?.trim() || null;
  if (color && !/^#[0-9a-f]{6}$/i.test(color)) {
    throw new Error($t('system.dict.errors.colorFormat'));
  }
  if (!form.label.trim() || !form.value.trim()) {
    throw new Error($t('system.dict.errors.labelValueRequired'));
  }
  return {
    dictTypeId: form.dictTypeId,
    label: form.label.trim(),
    value: form.value.trim(),
    color: color?.toLowerCase() || null,
    sort: form.sort,
    status: form.status,
    defaultItem: form.defaultItem,
    remark: form.remark,
  };
}

/**
 * 按相对亮度比较黑白文本对比度，颜色为空时交给默认主题。
 */
export function colorText(color: null | string): string | undefined {
  if (!color || !/^#[0-9a-f]{6}$/i.test(color)) {
    return undefined;
  }
  const channels = [1, 3, 5].map((i) => {
    const c = Number.parseInt(color.slice(i, i + 2), 16) / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  const luminance =
    0.2126 * channels[0]! + 0.7152 * channels[1]! + 0.0722 * channels[2]!;
  return (luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05)
    ? '#000000'
    : '#ffffff';
}
