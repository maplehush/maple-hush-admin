import { requestClient } from '#/api/request';

export interface DeptPayload {
  pid: string;
  name: string;
  sort: number;
  status: 0 | 1;
  remark: string;
}
export interface DeptRecord extends Omit<DeptPayload, 'remark'> {
  id: string;
  remark: null | string;
  createTime: string;
  updateTime: string;
  children: DeptRecord[];
}
export const getDeptList = () =>
  requestClient.get<DeptRecord[]>('/system/dept/list');
export const createDept = (data: DeptPayload) =>
  requestClient.post<{ id: string }>('/system/dept', data);
export const updateDept = (id: string, data: DeptPayload) =>
  requestClient.put(`/system/dept/${id}`, data);
export const deleteDept = (id: string) =>
  requestClient.delete(`/system/dept/${id}`);
