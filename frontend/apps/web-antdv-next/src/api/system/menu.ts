import { requestClient } from '#/api/request';

export namespace SystemMenuApi {
  export const BadgeVariants = [
    'default',
    'destructive',
    'primary',
    'success',
    'warning',
  ] as const;
  export const MenuTypes = [
    'catalog',
    'menu',
    'embedded',
    'link',
    'button',
  ] as const;
  export type SystemMenu = import('./menu').SystemMenu;
}

export type MenuType = 'button' | 'catalog' | 'embedded' | 'link' | 'menu';
export interface MenuMeta {
  title: string;
  icon?: string;
  order?: number;
  activeIcon?: string;
  activePath?: string;
  badge?: string;
  badgeType?: 'dot' | 'normal';
  badgeVariants?: 'default' | 'destructive' | 'primary' | 'success' | 'warning';
  iframeSrc?: string;
  link?: string;
  affixTab?: boolean;
  breadcrumbUseActivePath?: boolean;
  domCached?: boolean;
  fullPathKey?: boolean;
  hideChildrenInMenu?: boolean;
  hideInBreadcrumb?: boolean;
  hideInMenu?: boolean;
  hideInTab?: boolean;
  keepAlive?: boolean;
  noBasicLayout?: boolean;
  openInNewWindow?: boolean;
  affixTabOrder?: number;
  maxNumOfOpenTab?: number;
}
export interface MenuSave {
  pid?: string;
  name: string;
  type: MenuType;
  status: 0 | 1;
  path?: string;
  component?: string;
  redirect?: string;
  authCode?: string;
  meta: MenuMeta;
}
export interface SystemMenu extends MenuSave {
  id: string;
  children?: SystemMenu[];
}
export const getMenuList = () =>
  requestClient.get<SystemMenu[]>('/system/menu/list');
export const isMenuNameExists = (name: string, id?: string) =>
  requestClient.get<boolean>('/system/menu/name-exists', {
    params: { id, name },
  });
export const isMenuPathExists = (path: string, id?: string) =>
  requestClient.get<boolean>('/system/menu/path-exists', {
    params: { id, path },
  });
export const createMenu = (data: MenuSave) =>
  requestClient.post<{ id: string }>('/system/menu', data);
export const updateMenu = (id: string, data: MenuSave) =>
  requestClient.put<null>(`/system/menu/${id}`, data);
export const deleteMenu = (id: string) =>
  requestClient.delete<null>(`/system/menu/${id}`);
