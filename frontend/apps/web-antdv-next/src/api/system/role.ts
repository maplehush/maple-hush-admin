import { requestClient } from '#/api/request';

export namespace SystemRoleApi {
  export type SystemRole = import('./role').SystemRole;
}

export interface SystemRole {
  id: string;
  roleCode: string;
  name: string;
  status: 0 | 1;
  builtIn: boolean;
  remark?: string;
  permissions: string[];
  createTime?: string;
  updateTime?: string;
}
export interface RoleSave {
  roleCode?: string;
  name: string;
  status?: 0 | 1;
  remark?: string;
  permissions?: string[];
}
export interface RoleQuery {
  page: number;
  pageSize: number;
  name?: string;
  status?: number;
  startTime?: string;
  endTime?: string;
}
export const getRoleList = (params: RoleQuery) =>
  requestClient.get<{ items: SystemRole[]; total: number }>(
    '/system/role/list',
    { params },
  );
export const getRole = (id: string) =>
  requestClient.get<SystemRole>(`/system/role/${id}`);
export const createRole = (data: RoleSave) =>
  requestClient.post<{ id: string }>('/system/role', data);
export const updateRole = (id: string, data: RoleSave) =>
  requestClient.put<null>(`/system/role/${id}`, data);
export const deleteRole = (id: string) =>
  requestClient.delete<null>(`/system/role/${id}`);
