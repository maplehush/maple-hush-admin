import { requestClient } from '#/api/request';

export interface UserRecord {
  id: string;
  username: string;
  name: string;
  avatar: null | string;
  deptId: null | string;
  roleIds: string[];
  superAdmin: boolean;
  status: 0 | 1;
  remark: null | string;
  createTime: string;
  updateTime: string;
}
export interface UserPayload {
  name: string;
  deptId: null | string;
  status: 0 | 1;
  remark: string;
  avatarUploadId?: string;
}
export interface UserCreatePayload extends UserPayload {
  username: string;
  roleIds?: string[];
}
export interface UserQuery {
  page: number;
  pageSize: number;
  name?: string;
  deptId?: string;
  status?: number;
  startTime?: string;
  endTime?: string;
}
export interface UserRoleOption {
  id: string;
  name: string;
  roleCode: string;
  builtIn: boolean;
  status: number;
}
export interface UploadedImage {
  uploadId: string;
  url: string;
  name: string;
  size: number;
  contentType: string;
}
export const getUserList = (params: UserQuery) =>
  requestClient.get<{ items: UserRecord[]; total: number }>(
    '/system/user/list',
    { params },
  );
export const getUser = (id: string) =>
  requestClient.get<UserRecord>(`/system/user/${id}`);
export const createUser = (data: UserCreatePayload) =>
  requestClient.post<{ id: string }>('/system/user', data);
export const updateUser = (id: string, data: UserPayload) =>
  requestClient.put(`/system/user/${id}`, data);
export const deleteUser = (id: string) =>
  requestClient.delete(`/system/user/${id}`);
export const assignUserRoles = (id: string, roleIds: string[]) =>
  requestClient.put(`/system/user/${id}/roles`, { roleIds });
export const resetUserPassword = (id: string) =>
  requestClient.post(`/system/user/${id}/reset-password`);
export async function getAssignableRoleOptions() {
  const result: UserRoleOption[] = [];
  for (let page = 1; ; page++) {
    const data = await requestClient.get<{
      items: UserRoleOption[];
      total: number;
    }>('/system/role/list', { params: { page, pageSize: 100 } });
    result.push(...data.items);
    if (result.length >= data.total) break;
    if (data.items.length === 0)
      throw new Error('角色分页数据不完整，请刷新后重试');
  }
  return result.filter(
    (role) => !role.builtIn && role.roleCode !== 'super_admin',
  );
}
export const uploadUserImage = (file: File) => {
  return requestClient.upload<UploadedImage>('/files/upload', { file });
};
