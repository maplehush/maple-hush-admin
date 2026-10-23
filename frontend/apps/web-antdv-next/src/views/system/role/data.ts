import type { VbenFormSchema } from '#/adapter/form';
import type { OnActionClickFn, VxeTableGridColumns } from '#/adapter/vxe-table';
import type { SystemMenu } from '#/api/system/menu';
import type { RoleSave, SystemRole } from '#/api/system/role';

import { useAccessStore } from '@vben/stores';

import { z } from '#/adapter/form';
import { $t } from '#/locales';

/**
 * 角色表单字段配置选项，用于区分新增、编辑和授权模式。
 */
interface RoleFormSchemaOptions {
  canGrant?: boolean;
  mode?: 'create' | 'edit' | 'grant';
  roleCodeDisabled?: boolean;
  statusDisabled?: boolean;
}

/**
 * 根据新增、编辑或菜单授权场景生成对应表单。
 */
export function useFormSchema({
  canGrant = false,
  mode = 'create',
  roleCodeDisabled = false,
  statusDisabled = false,
}: RoleFormSchemaOptions = {}): VbenFormSchema[] {
  const permissionsSchema: VbenFormSchema = {
    component: 'Input',
    fieldName: 'permissions',
    formItemClass: 'items-start',
    label: $t(
      mode === 'grant' ? 'system.role.grantMenu' : 'system.role.setPermissions',
    ),
    modelPropName: 'modelValue',
  };

  if (mode === 'grant') {
    return [permissionsSchema];
  }

  const schema: VbenFormSchema[] = [
    {
      component: 'Input',
      fieldName: 'roleCode',
      label: $t('system.role.roleCode'),
      componentProps: { disabled: roleCodeDisabled },
      rules: z
        .string()
        .regex(
          /^[a-z][a-z0-9_]{0,63}$/,
          $t('system.role.errors.roleCodeFormat'),
        ),
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('system.role.roleName'),
      rules: z
        .string({ error: $t('system.role.errors.nameRequired') })
        .trim()
        .min(1, $t('system.role.errors.nameRequired'))
        .max(100, $t('system.role.errors.nameMax')),
    },
    {
      component: 'RadioGroup',
      componentProps: {
        buttonStyle: 'solid',
        options: [
          { label: $t('common.enabled'), value: 1, disabled: statusDisabled },
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
      rules: z.string().max(500, $t('system.role.errors.remarkMax')).optional(),
    },
  ];
  if (mode === 'create' && canGrant) {
    schema.push(permissionsSchema);
  }
  return schema;
}

/**
 * 构造角色列表的筛选表单。
 */
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

/**
 * 构造角色表格列，并根据内置角色和权限决定可用操作。
 */
export function useColumns(
  onActionClick: OnActionClickFn<SystemRole>,
  onStatusChange: (status: 0 | 1, row: SystemRole) => Promise<boolean>,
): VxeTableGridColumns<SystemRole> {
  const access = useAccessStore();
  const can = (action: string) =>
    access.accessCodes.includes(`system:role:${action}`);
  return [
    { field: 'name', title: $t('system.role.roleName'), width: 200 },
    { field: 'roleCode', title: $t('system.role.roleCode'), width: 180 },
    { field: 'id', title: $t('system.role.id'), width: 100 },
    {
      cellRender: {
        attrs: {
          beforeChange: onStatusChange,
          isDisabled: (row: SystemRole) =>
            row.builtIn ||
            !can('update') ||
            (row.status === 0 && !can('grant')),
        },
        name: 'CellSwitch',
      },
      field: 'status',
      title: $t('system.role.status'),
      width: 100,
    },
    {
      field: 'builtIn',
      title: $t('system.role.builtIn'),
      width: 100,
      formatter: ({ row }) =>
        row.builtIn ? $t('system.role.builtInProtected') : $t('common.no'),
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
            text: $t('system.role.grantMenu'),
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

/**
 * 角色授权树、权限回显和角色提交数据处理。
 */
export const rolePermissionTreeProps = {
  multiple: true,
  checkStrictly: false,
} as const;

/**
 * 从角色可授权菜单中移除共用工作台入口和空目录。
 */
export function roleGrantMenuTree(menus: SystemMenu[]): SystemMenu[] {
  return menus.flatMap((menu) => {
    const children = menu.children
      ? roleGrantMenuTree(menu.children)
      : undefined;
    if (menu.name === 'Workspace') return [];
    if (menu.type === 'catalog' && !children?.length) return [];
    return [{ ...menu, ...(children ? { children } : {}) }];
  });
}

/**
 * 根据已授权后代重建全选目录；半选目录留给树组件计算展示。
 */
export function rolePermissionValues(
  menus: SystemMenu[],
  selectedIds: string[],
): string[] {
  const menuById = new Map<string, SystemMenu>();
  const indexMenus = (nodes: SystemMenu[]) => {
    for (const menu of nodes) {
      menuById.set(menu.id, menu);
      if (menu.children) {
        indexMenus(menu.children);
      }
    }
  };
  indexMenus(menus);

  const selected = new Set(
    selectedIds.filter(
      (id) => menuById.has(id) && menuById.get(id)?.type !== 'catalog',
    ),
  );
  const isFullySelected = (menu: SystemMenu): boolean => {
    if (menu.type !== 'catalog') {
      return selected.has(menu.id);
    }
    const children = menu.children ?? [];
    return children.length > 0 && children.every(isFullySelected);
  };

  const addFullySelectedDirectories = (nodes: SystemMenu[]) => {
    for (const menu of nodes) {
      if (menu.type === 'catalog' && isFullySelected(menu)) {
        selected.add(menu.id);
      }
      if (menu.children) {
        addFullySelectedDirectories(menu.children);
      }
    }
  };
  addFullySelectedDirectories(menus);

  return [...selected];
}

/**
 * 合并表单变更；仅修改菜单授权时保留角色其余属性。
 */
export function mergeRoleValues(
  current: SystemRole | undefined,
  submitted: Partial<RoleSave>,
  permissionsOnly = false,
): RoleSave {
  if (current && permissionsOnly) {
    return {
      ...current,
      permissions: [...(submitted.permissions ?? [])],
    };
  }

  const merged = { ...current };
  for (const [key, value] of Object.entries(submitted)) {
    if (value !== undefined) {
      Object.assign(merged, { [key]: value });
    }
  }
  return merged as RoleSave;
}

/**
 * 生成角色新增、编辑或授权请求数据，避免发送未编辑字段。
 */
export function rolePayload(
  value: RoleSave,
  creating: boolean,
  grant = false,
): RoleSave {
  return {
    name: value.name.trim(),
    remark: value.remark?.trim() ?? '',
    status: value.status,
    ...(creating ? { roleCode: value.roleCode?.trim() } : {}),
    ...(grant ? { permissions: [...(value.permissions ?? [])] } : {}),
  };
}

/**
 * 判断角色状态或权限变化是否会影响当前登录用户。
 */
export function affectsCurrentRoles(
  before: SystemRole,
  after: RoleSave,
  roles: string[],
) {
  return (
    roles.includes(before.roleCode) &&
    ((after.status !== undefined && before.status !== after.status) ||
      (after.permissions !== undefined &&
        [...new Set(before.permissions)].sort().join(',') !==
          [...new Set(after.permissions)].sort().join(',')))
  );
}
