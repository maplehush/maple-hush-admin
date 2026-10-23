<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { MenuSave, MenuType } from '#/api/system/menu';

import { computed, ref, watch } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { $te } from '@vben/locales';
import { useAccessStore } from '@vben/stores';

import { breakpointsTailwind, useBreakpoints } from '@vueuse/core';
import { message } from 'antdv-next';

import { useVbenForm, z } from '#/adapter/form';
import {
  createMenu,
  getMenuList,
  isMenuNameExists,
  isMenuPathExists,
  SystemMenuApi,
  updateMenu,
} from '#/api/system/menu';
import { $t } from '#/locales';
import { refreshAccessRoutes } from '#/router/refresh-access';

import {
  getMenuTypeOptions,
  isMenuParentRequired,
  MENU_PATH_PATTERN,
  menuPayload,
  optionalActivePathRule,
  parentOptions,
  validateMenuNameAvailability,
  validateMenuPathAvailability,
} from '../data';

const emit = defineEmits<{ success: [] }>();

const componentKeys = [
  '/system/user/list',
  '/system/role/list',
  '/system/menu/list',
  '/system/dept/list',
  '/system/dict/list',
  '/log/login/list',
  '/log/operation/list',
];

const formData = ref<SystemMenuApi.SystemMenu>();
const ready = ref(false);
const titleSuffix = ref<string>();
const menuTree = ref<SystemMenuApi.SystemMenu[]>([]);
const access = useAccessStore();
const can = (action: string) =>
  access.accessCodes.includes(`system:menu:${action}`);

type MenuDrawerData =
  | SystemMenuApi.SystemMenu
  | { pid?: string; type?: MenuType };
type MenuSubmitValues = MenuSave;
type MenuFormValues = MenuSubmitValues & { linkSrc?: string };

function encodeMenuFormValues(
  values: Readonly<MenuFormValues>,
): MenuSubmitValues {
  const { linkSrc, ...formValues } = values;
  const meta = { ...formValues.meta };
  meta.title ??= '';
  if (values.type === 'link') meta.link = linkSrc;
  if (values.type === 'embedded') meta.iframeSrc = linkSrc;
  return menuPayload({ ...formValues, meta }, formData.value?.meta);
}

function decodeMenuFormValues(
  values: Readonly<MenuSubmitValues>,
): MenuFormValues {
  let linkSrc: string | undefined;
  if (values.type === 'link') {
    linkSrc = values.meta?.link;
  } else if (values.type === 'embedded') {
    linkSrc = values.meta?.iframeSrc;
  }
  return {
    ...values,
    linkSrc,
    pid: values.pid ? String(values.pid) : undefined,
  };
}

const schema = computed<VbenFormSchema<MenuFormValues>[]>(() => [
  {
    component: 'RadioGroup',
    componentProps: {
      buttonStyle: 'solid',
      options: getMenuTypeOptions(),
      optionType: 'button',
    },
    defaultValue: 'catalog',
    fieldName: 'type',
    formItemClass: 'col-span-2 md:col-span-2',
    label: $t('system.menu.type'),
  },
  {
    component: 'Input',
    fieldName: 'name',
    label: $t('system.menu.menuName'),
    rules: z
      .string()
      .trim()
      .min(1, $t('system.menu.nameRequired'))
      .regex(/^[A-Za-z][A-Za-z0-9_-]{0,99}$/, $t('system.menu.nameFormat'))
      .refine(
        async (value: string) =>
          validateMenuNameAvailability(
            value,
            (name) => isMenuNameExists(name, formData.value?.id),
            $t('system.menu.nameCheckFailed'),
          ),
        {
          error: (issue) =>
            $t('ui.formRules.alreadyExists', [
              $t('system.menu.menuName'),
              issue.input,
            ]),
        },
      ),
  },
  {
    component: 'TreeSelect',
    componentProps: {
      allowClear: true,
      showSearch: true,
      treeDefaultExpandAll: true,
      class: 'w-full',
    },
    dependencies: {
      rules: (values) =>
        isMenuParentRequired(values.type ?? 'catalog') ? 'required' : null,
      triggerFields: ['type'],
      componentProps: (values) => ({
        treeData: parentOptions(
          menuTree.value,
          formData.value?.id,
          values.type ?? 'catalog',
        ),
      }),
    },
    fieldName: 'pid',
    label: $t('system.menu.parent'),
  },
  {
    component: 'Input',
    componentProps() {
      // 不需要处理多语言时就无需这么做
      return {
        ...(titleSuffix.value && { addonAfter: titleSuffix.value }),
        onChange({ target: { value } }: { target: { value: string } }) {
          titleSuffix.value = value && $te(value) ? $t(value) : undefined;
        },
      };
    },
    fieldName: 'meta.title',
    label: $t('system.menu.menuTitle'),
    rules: z.string().trim().min(1, $t('system.menu.titleRequired')).max(200),
  },
  {
    component: 'Input',
    dependencies: {
      rules: (values) =>
        ['button', 'link'].includes(values.type ?? 'menu')
          ? null
          : z
              .string()
              .trim()
              .min(1, $t('system.menu.pathRequired'))
              .regex(MENU_PATH_PATTERN, $t('system.menu.pathFormat'))
              .max(255)
              .refine(
                async (value: string) =>
                  validateMenuPathAvailability(
                    value,
                    (path) => isMenuPathExists(path, formData.value?.id),
                    $t('system.menu.pathCheckFailed'),
                  ),
                {
                  error: (issue) =>
                    $t('ui.formRules.alreadyExists', [
                      $t('system.menu.path'),
                      issue.input,
                    ]),
                },
              ),
      show: (values) => !['button', 'link'].includes(values.type ?? 'menu'),
      triggerFields: ['type'],
    },
    fieldName: 'path',
    label: $t('system.menu.path'),
  },
  {
    component: 'Input',
    dependencies: {
      rules: (values) => {
        if (!['embedded', 'link'].includes(values.type ?? 'menu')) return null;
        return z.url($t('ui.formRules.invalidURL')).refine((value) => {
          try {
            const url = new URL(value);
            return (
              ['http:', 'https:'].includes(url.protocol) &&
              !url.username &&
              !url.password
            );
          } catch {
            return false;
          }
        }, $t('system.menu.linkFormat'));
      },
      show: (values) => {
        return ['embedded', 'link'].includes(values.type ?? 'menu');
      },
      triggerFields: ['type'],
    },
    fieldName: 'meta.activePath',
    help: $t('system.menu.activePathHelp'),
    label: $t('system.menu.activePath'),
    rules: optionalActivePathRule((path) => isMenuPathExists(path)),
  },
  {
    component: 'IconPicker',
    componentProps: {
      prefix: 'carbon',
    },
    dependencies: {
      show: () => {
        return true;
      },
      triggerFields: ['type'],
    },
    fieldName: 'meta.icon',
    label: $t('system.menu.icon'),
  },
  {
    component: 'IconPicker',
    componentProps: {
      prefix: 'carbon',
    },
    dependencies: {
      show: (values) => {
        return ['catalog', 'embedded', 'menu'].includes(values.type ?? 'menu');
      },
      triggerFields: ['type'],
    },
    fieldName: 'meta.activeIcon',
    label: $t('system.menu.activeIcon'),
  },
  {
    component: 'AutoComplete',
    componentProps: {
      allowClear: true,
      class: 'w-full',
      filterOption(input: string, option: { value: string }) {
        return option.value.toLowerCase().includes(input.toLowerCase());
      },
      options: [...componentKeys, 'BasicLayout'].map((v) => ({ value: v })),
    },
    dependencies: {
      rules: (values) => {
        return values.type === 'menu' ? 'required' : null;
      },
      show: (values) => {
        return (
          values.type === 'menu' || (values.type === 'catalog' && !values.pid)
        );
      },
      triggerFields: ['type', 'pid'],
    },
    fieldName: 'component',
    label: $t('system.menu.component'),
  },
  {
    component: 'Input',
    dependencies: {
      show: (values) => {
        return ['embedded', 'link'].includes(values.type ?? 'menu');
      },
      triggerFields: ['type'],
    },
    fieldName: 'linkSrc',
    label: $t('system.menu.linkSrc'),
  },
  {
    component: 'Input',
    dependencies: {
      rules: (values) => {
        return values.type === 'button' ? 'required' : null;
      },
      show: (values) => {
        return values.type !== 'catalog';
      },
      triggerFields: ['type'],
    },
    fieldName: 'authCode',
    label: $t('system.menu.authCode'),
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
    label: $t('system.menu.status'),
  },
  {
    component: 'Select',
    componentProps: {
      allowClear: true,
      class: 'w-full',
      options: [
        { label: $t('system.menu.badgeType.dot'), value: 'dot' },
        { label: $t('system.menu.badgeType.normal'), value: 'normal' },
      ],
    },
    dependencies: {
      show: (values) => {
        return values.type !== 'button';
      },
      triggerFields: ['type'],
    },
    fieldName: 'meta.badgeType',
    label: $t('system.menu.badgeType.title'),
  },
  {
    component: 'Input',
    dependencies: {
      resolve: ({ values }) => {
        return {
          componentProps: {
            allowClear: true,
            class: 'w-full',
            disabled: values.meta?.badgeType !== 'normal',
          },
          show: values.type !== 'button',
        };
      },
      triggerFields: ['meta.badgeType', 'type'],
    },
    fieldName: 'meta.badge',
    label: $t('system.menu.badge'),
  },
  {
    component: 'Select',
    componentProps: {
      allowClear: true,
      class: 'w-full',
      options: SystemMenuApi.BadgeVariants.map((v) => ({
        label: v,
        value: v,
      })),
    },
    dependencies: {
      show: (values) => {
        return values.type !== 'button';
      },
      triggerFields: ['type'],
    },
    fieldName: 'meta.badgeVariants',
    label: $t('system.menu.badgeVariants'),
  },
  {
    component: 'Divider',
    dependencies: {
      show: (values) => {
        return !['button', 'link'].includes(values.type ?? 'menu');
      },
      triggerFields: ['type'],
    },
    fieldName: 'divider1',
    formItemClass: 'col-span-2 md:col-span-2 pb-0',
    hideLabel: true,
    renderComponentContent() {
      return {
        default: () => $t('system.menu.advancedSettings'),
      };
    },
  },
  {
    component: 'Checkbox',
    dependencies: {
      show: (values) => {
        return ['menu'].includes(values.type ?? 'menu');
      },
      triggerFields: ['type'],
    },
    fieldName: 'meta.keepAlive',
    renderComponentContent() {
      return {
        default: () => $t('system.menu.keepAlive'),
      };
    },
  },
  {
    component: 'Checkbox',
    dependencies: {
      show: (values) => {
        return ['embedded', 'menu'].includes(values.type ?? 'menu');
      },
      triggerFields: ['type'],
    },
    fieldName: 'meta.affixTab',
    renderComponentContent() {
      return {
        default: () => $t('system.menu.affixTab'),
      };
    },
  },
  {
    component: 'Checkbox',
    dependencies: {
      show: (values) => {
        return !['button'].includes(values.type ?? 'menu');
      },
      triggerFields: ['type'],
    },
    fieldName: 'meta.hideInMenu',
    renderComponentContent() {
      return {
        default: () => $t('system.menu.hideInMenu'),
      };
    },
  },
  {
    component: 'Checkbox',
    dependencies: {
      show: (values) => {
        return ['catalog', 'menu'].includes(values.type ?? 'menu');
      },
      triggerFields: ['type'],
    },
    fieldName: 'meta.hideChildrenInMenu',
    renderComponentContent() {
      return {
        default: () => $t('system.menu.hideChildrenInMenu'),
      };
    },
  },
  {
    component: 'Checkbox',
    dependencies: {
      show: (values) => {
        return !['button', 'link'].includes(values.type ?? 'menu');
      },
      triggerFields: ['type'],
    },
    fieldName: 'meta.hideInBreadcrumb',
    renderComponentContent() {
      return {
        default: () => $t('system.menu.hideInBreadcrumb'),
      };
    },
  },
  {
    component: 'Checkbox',
    dependencies: {
      show: (values) => {
        return !['button', 'link'].includes(values.type ?? 'menu');
      },
      triggerFields: ['type'],
    },
    fieldName: 'meta.hideInTab',
    renderComponentContent() {
      return {
        default: () => $t('system.menu.hideInTab'),
      };
    },
  },
  {
    component: 'InputNumber',
    fieldName: 'meta.order',
    label: $t('system.menu.sort'),
    defaultValue: 0,
    componentProps: { precision: 0 },
  },
  {
    component: 'Input',
    fieldName: 'redirect',
    label: $t('system.menu.redirect'),
    dependencies: {
      show: (values) => !['button', 'link'].includes(values.type ?? 'menu'),
      triggerFields: ['type'],
    },
  },
]);

const breakpoints = useBreakpoints(breakpointsTailwind);
const isHorizontal = computed(() => breakpoints.greaterOrEqual('md').value);

const [Form, formApi] = useVbenForm({
  codec: {
    decode: decodeMenuFormValues,
    encode: encodeMenuFormValues,
  },
  commonConfig: {
    colon: true,
    formItemClass: 'col-span-2 md:col-span-1',
  },
  schema: schema.value,
  showDefaultActions: false,
  wrapperClass: 'grid-cols-2 gap-x-4',
});
watch(schema, (nextSchema) => formApi.setState({ schema: nextSchema }));
const [Drawer, drawerApi] = useVbenDrawer<MenuDrawerData>({
  onConfirm: onSubmit,
  async onOpenChange(isOpen) {
    ready.value = false;
    if (isOpen) {
      drawerApi.setState({ loading: true, showConfirmButton: false });
      try {
        const data = drawerApi.getData();
        if (!access.accessCodes.includes('system:menu:list')) {
          drawerApi.close();
          return;
        }
        menuTree.value = await getMenuList();
        await formApi.reset();
        if (data && 'id' in data) {
          formData.value = data;
          await formApi.setSubmitValues(data);
          titleSuffix.value = formData.value.meta?.title
            ? $t(formData.value.meta.title)
            : '';
        } else {
          formData.value = undefined;
          formApi.reset();
          await formApi.setValues(data ?? {});
          titleSuffix.value = '';
        }
        ready.value = true;
        drawerApi.setState({ showConfirmButton: true });
      } catch {
        message.error($t('system.menu.loadError'));
      } finally {
        drawerApi.setState({ loading: false });
      }
    }
  },
});

defineExpose({ drawerApi });

async function onSubmit() {
  if (!ready.value) return;
  const { valid } = await formApi.validate();
  if (!valid) return;

  drawerApi.lock();
  try {
    const data = await formApi.getValues();
    if (formData.value ? !can('update') : !can('create')) return;
    if (data.type === 'button' && !data.pid) {
      message.warning($t('system.menu.buttonParentRequired'));
      return;
    }

    await (formData.value?.id
      ? updateMenu(formData.value.id, data)
      : createMenu(data));
    drawerApi.close();
    emit('success');
    try {
      await refreshAccessRoutes();
      message.success($t('system.menu.saved'));
    } catch {
      message.warning($t('system.menu.refreshFailed'));
    }
  } finally {
    drawerApi.unlock();
  }
}
const getDrawerTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', [$t('system.menu.name')])
    : $t('ui.actionTitle.create', [$t('system.menu.name')]),
);
</script>
<template>
  <Drawer class="w-full max-w-200" :title="getDrawerTitle">
    <Form class="mx-4" :layout="isHorizontal ? 'horizontal' : 'vertical'" />
  </Drawer>
</template>
