import type {
  MenuMeta,
  MenuSave,
  MenuType,
  SystemMenu,
} from '#/api/system/menu';

import { z } from '#/adapter/form';

export function optionalActivePathRule(
  exists: (path: string) => Promise<boolean>,
) {
  // Vben 会同步探测 isOptional；外层 optional 必须先拦截 undefined。
  return z
    .string()
    .transform(normalizeOptionalPath)
    .refine(
      (value) => validateOptionalActivePath(value, exists),
      '请输入已存在的完整静态路径，或留空',
    )
    .optional();
}

export function normalizeOptionalPath(value?: string): string | undefined {
  return value?.trim() || undefined;
}

export async function validateOptionalActivePath(
  value: string | undefined,
  exists: (path: string) => Promise<boolean>,
): Promise<boolean> {
  const path = normalizeOptionalPath(value);
  if (path === undefined) return true;
  if (
    path.length > 255 ||
    !/^\/[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)*$/.test(path)
  )
    return false;
  return await exists(path);
}

export function menuPayload(
  value: MenuSave,
  originalMeta?: Readonly<MenuMeta>,
): MenuSave {
  // 编辑基础字段时保留表单未展示的原有高级参数，新建菜单不引入这些参数。
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
    pid: value.pid || undefined,
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
    path: value.path?.trim(),
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
export interface ParentOption {
  value: string;
  title: string;
  children?: ParentOption[];
}
export function parentOptions(
  nodes: SystemMenu[],
  excluded: string | undefined,
  type: MenuType,
): ParentOption[] {
  return nodes
    .filter((node) => node.id !== excluded)
    .flatMap((node) => {
      const children = parentOptions(node.children ?? [], excluded, type);
      const allowed =
        type === 'button'
          ? ['embedded', 'link', 'menu'].includes(node.type)
          : node.type === 'catalog';
      return allowed
        ? [
            {
              value: node.id,
              title: `${node.meta.title} (${node.name})`,
              ...(children.length ? { children } : {}),
            },
          ]
        : children;
    });
}
