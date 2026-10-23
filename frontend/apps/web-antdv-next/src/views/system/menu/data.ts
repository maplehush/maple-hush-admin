import type { OnActionClickFn, VxeTableGridColumns } from '#/adapter/vxe-table';
import type {
  MenuMeta,
  MenuSave,
  MenuType,
  SystemMenu,
  SystemMenuApi,
} from '#/api/system/menu';

import { useAccessStore } from '@vben/stores';

import { z } from '#/adapter/form';
import { $t } from '#/locales';

/**
 * 返回菜单类型选择器所需的文案和颜色。
 */
export function getMenuTypeOptions() {
  return [
    {
      color: 'processing',
      label: $t('system.menu.typeCatalog'),
      value: 'catalog',
    },
    { color: 'default', label: $t('system.menu.typeMenu'), value: 'menu' },
    { color: 'error', label: $t('system.menu.typeButton'), value: 'button' },
    {
      color: 'success',
      label: $t('system.menu.typeEmbedded'),
      value: 'embedded',
    },
    { color: 'warning', label: $t('system.menu.typeLink'), value: 'link' },
  ];
}

/**
 * 构造菜单树表格列，并依据当前权限显示可用操作。
 */
export function useColumns(
  onActionClick: OnActionClickFn<SystemMenuApi.SystemMenu>,
  isDeleteDisabled: (row: SystemMenuApi.SystemMenu) => boolean = () => false,
): VxeTableGridColumns<SystemMenuApi.SystemMenu> {
  const access = useAccessStore();
  const can = (action: string) =>
    access.accessCodes.includes(`system:menu:${action}`);
  return [
    {
      align: 'left',
      field: 'meta.title',
      fixed: 'left',
      slots: { default: 'title' },
      title: $t('system.menu.menuTitle'),
      treeNode: true,
      width: 250,
    },
    {
      align: 'center',
      cellRender: { name: 'CellTag', options: getMenuTypeOptions() },
      field: 'type',
      title: $t('system.menu.type'),
      width: 100,
    },
    {
      field: 'authCode',
      title: $t('system.menu.authCode'),
      width: 200,
    },
    {
      align: 'left',
      field: 'path',
      title: $t('system.menu.path'),
      width: 200,
    },

    {
      align: 'left',
      field: 'component',
      formatter: ({ row }) => {
        switch (row.type) {
          case 'catalog':
          case 'menu': {
            return row.component ?? '';
          }
          case 'embedded': {
            return row.meta?.iframeSrc ?? '';
          }
          case 'link': {
            return row.meta?.link ?? '';
          }
        }
        return '';
      },
      minWidth: 200,
      title: $t('system.menu.component'),
    },
    {
      cellRender: { name: 'CellTag' },
      field: 'status',
      title: $t('system.menu.status'),
      width: 100,
    },

    {
      align: 'right',
      cellRender: {
        attrs: {
          nameField: 'name',
          onClick: onActionClick,
        },
        name: 'CellOperation',
        options: [
          {
            code: 'append',
            text: $t('system.menu.append'),
            show: (row: SystemMenuApi.SystemMenu) =>
              row.type !== 'button' && can('create'),
          },
          { code: 'edit', show: () => can('update') },
          {
            code: 'delete',
            disabled: isDeleteDisabled,
            show: () => can('delete'),
          },
        ],
      },
      field: 'operation',
      fixed: 'right',
      headerAlign: 'center',
      showOverflow: false,
      title: $t('system.menu.operation'),
      width: 200,
    },
  ];
}

/**
 * 菜单表单校验、父级选项和 API 提交 DTO 处理。
 */

/**
 * 菜单标识仅允许字母开头及字母、数字、下划线和短横线。
 */
const MENU_NAME_PATTERN = /^[A-Za-z][A-Za-z0-9_-]{0,99}$/;

/**
 * 菜单路由地址只允许静态路径片段，不接受查询串、参数段或尾部斜杠。
 */
export const MENU_PATH_PATTERN = /^\/[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)*$/;

/**
 * 校验菜单名称；格式正确时才请求后端检查唯一性。
 */
export async function validateMenuNameAvailability(
  value: string,
  exists: (name: string) => Promise<boolean>,
  requestError: string,
): Promise<boolean> {
  const name = value.trim();
  if (!name || !MENU_NAME_PATTERN.test(name)) return true;

  try {
    return !(await exists(name));
  } catch {
    throw new Error(requestError);
  }
}

/**
 * 校验菜单路由地址；格式正确时才请求后端检查唯一性。
 */
export async function validateMenuPathAvailability(
  value: string,
  exists: (path: string) => Promise<boolean>,
  requestError: string,
): Promise<boolean> {
  const path = value.trim();
  if (!path || path.length > 255 || !MENU_PATH_PATTERN.test(path)) return true;

  try {
    return !(await exists(path));
  } catch {
    throw new Error(requestError);
  }
}

/**
 * 创建可选激活路由规则，填写时要求目标路由存在。
 */
export function optionalActivePathRule(
  exists: (path: string) => Promise<boolean>,
) {
  /**
   * Vben 会同步探测 isOptional；外层 optional 必须先拦截 undefined。
   */
  return z
    .string()
    .transform(normalizeOptionalPath)
    .refine(
      (value) => validateOptionalActivePath(value, exists),
      $t('system.menu.activePathMustExist'),
    )
    .optional();
}

/**
 * 将空白激活路由统一转换为 undefined，支持清空字段。
 */
export function normalizeOptionalPath(value?: string): string | undefined {
  return value?.trim() || undefined;
}

/**
 * 检查激活路由格式并确认目标菜单存在。
 */
export async function validateOptionalActivePath(
  value: string | undefined,
  exists: (path: string) => Promise<boolean>,
): Promise<boolean> {
  const path = normalizeOptionalPath(value);
  if (path === undefined) return true;
  if (path.length > 255 || !MENU_PATH_PATTERN.test(path)) return false;
  return await exists(path);
}

/**
 * 清理表单数据并生成与菜单类型匹配的后端 DTO。
 */
export function menuPayload(
  value: MenuSave,
  originalMeta?: Readonly<MenuMeta>,
): MenuSave {
  /**
   * 编辑基础字段时保留表单未展示的原有高级参数，新建菜单不引入这些参数。
   */
  const hiddenKeys = [
    'affixTabOrder',
    'maxNumOfOpenTab',
    'breadcrumbUseActivePath',
    'domCached',
    'fullPathKey',
    'noBasicLayout',
    'openInNewWindow',
  ] as const;
  const retained = Object.fromEntries(
    hiddenKeys
      .filter((key) => originalMeta?.[key] !== undefined)
      .map((key) => [key, originalMeta![key]]),
  ) as Partial<MenuMeta>;
  const meta = { ...retained, ...value.meta };
  for (const key of Object.keys(meta) as (keyof typeof meta)[]) {
    const field = meta[key];
    if (
      field === null ||
      field === undefined ||
      (key !== 'title' && typeof field === 'string' && !field.trim())
    )
      delete meta[key];
  }
  if (meta.activePath !== undefined)
    meta.activePath = normalizeOptionalPath(meta.activePath);
  meta.title = value.meta.title.trim();
  if (value.type !== 'link') delete meta.link;
  if (value.type !== 'embedded') delete meta.iframeSrc;
  const common = {
    pid: value.pid ? String(value.pid) : undefined,
    name: value.name.trim(),
    type: value.type,
    status: value.status,
    meta,
  };
  if (value.type === 'button') {
    return {
      ...common,
      authCode: value.authCode?.trim(),
      meta: { title: meta.title.trim(), icon: meta.icon, order: meta.order },
    };
  }
  return {
    ...common,
    path: value.type === 'link' ? undefined : value.path?.trim(),
    authCode: value.type === 'catalog' ? undefined : value.authCode?.trim(),
    component:
      value.type === 'embedded'
        ? 'IFrameView'
        : value.type === 'link'
          ? undefined
          : value.type === 'catalog'
            ? !value.pid && value.component === 'BasicLayout'
              ? 'BasicLayout'
              : undefined
            : value.component?.trim(),
    redirect: value.type === 'link' ? undefined : value.redirect?.trim(),
  };
}

/**
 * 菜单父级选择器使用的树节点结构。
 */
export interface ParentOption {
  value: string;
  title: string;
  children?: ParentOption[];
}

/**
 * 判断当前菜单类型是否必须选择上级菜单。
 */
export function isMenuParentRequired(type: MenuType): boolean {
  return type !== 'catalog' && type !== 'link';
}

/**
 * 构造合法父级选项，并排除当前节点以防止菜单树形成循环。
 */
export function parentOptions(
  nodes: SystemMenu[],
  excluded: string | undefined,
  type: MenuType,
): ParentOption[] {
  return nodes
    .filter((node) => String(node.id) !== excluded)
    .flatMap((node) => {
      const children = parentOptions(node.children ?? [], excluded, type);
      const allowed =
        type === 'button'
          ? ['embedded', 'link', 'menu'].includes(node.type)
          : node.type === 'catalog';
      return allowed
        ? [
            {
              value: String(node.id),
              title: `${node.meta.title} (${node.name})`,
              ...(children.length ? { children } : {}),
            },
          ]
        : children;
    });
}

/**
 * 菜单删除保护规则：系统菜单树与基础工作台路由不可删除。
 */
interface MenuNode {
  children?: readonly MenuNode[];
  id: string;
  name: string;
}

/**
 * 收集系统菜单子树和基础工作台路由，供删除保护逻辑使用。
 */
export function collectUndeletableMenuIds(
  menus: readonly MenuNode[],
): Set<string> {
  const ids = new Set<string>();

  function visit(nodes: readonly MenuNode[], insideSystem = false) {
    for (const node of nodes) {
      const isSystemNode = insideSystem || node.name === 'System';
      const isBaselineRoute =
        node.name === 'Dashboard' || node.name === 'Workspace';

      if (isSystemNode || isBaselineRoute) {
        ids.add(node.id);
      }
      if (node.children?.length) {
        visit(node.children, isSystemNode);
      }
    }
  }

  visit(menus);
  return ids;
}
