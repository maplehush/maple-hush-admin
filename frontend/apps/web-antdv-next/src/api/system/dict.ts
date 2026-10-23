import { requestClient } from '#/api/request';
export interface DictType {
  id: string;
  dictCode: string;
  name: string;
  status: number;
  remark: null | string;
  createTime: string;
  updateTime: string;
}
export interface DictItem {
  id: string;
  dictTypeId: string;
  label: string;
  value: string;
  color: null | string;
  sort: number;
  defaultItem: boolean;
  status: number;
  remark: null | string;
  createTime: string;
  updateTime: string;
}
export interface DictOption {
  label: string;
  value: string;
  color: null | string;
  defaultItem: boolean;
}
export interface PageResult<T> {
  items: T[];
  total: number;
}
export type DictTypeInput = Pick<DictType, 'dictCode' | 'name' | 'status'> & {
  remark?: string;
};
export type DictItemInput = Omit<DictItem, 'createTime' | 'id' | 'updateTime'>;
export type DictStatus = 0 | 1;
export const dictApi = {
  types: (params: Record<string, unknown>) =>
    requestClient.get<PageResult<DictType>>('/system/dict/type/list', {
      params,
    }),
  type: (id: string) => requestClient.get<DictType>(`/system/dict/type/${id}`),
  saveType: (id: string | undefined, data: DictTypeInput) =>
    id
      ? requestClient.put(`/system/dict/type/${id}`, data)
      : requestClient.post('/system/dict/type', data),
  updateTypeStatus: async (id: string, status: DictStatus) => {
    const current = await requestClient.get<DictType>(
      `/system/dict/type/${id}`,
    );
    return requestClient.put(`/system/dict/type/${id}`, {
      dictCode: current.dictCode,
      name: current.name,
      remark: current.remark ?? '',
      status,
    } satisfies DictTypeInput);
  },
  removeType: (id: string) => requestClient.delete(`/system/dict/type/${id}`),
  items: (params: Record<string, unknown>) =>
    requestClient.get<PageResult<DictItem>>('/system/dict/item/list', {
      params,
    }),
  item: (id: string) => requestClient.get<DictItem>(`/system/dict/item/${id}`),
  saveItem: (id: string | undefined, data: DictItemInput) =>
    id
      ? requestClient.put(`/system/dict/item/${id}`, data)
      : requestClient.post('/system/dict/item', data),
  updateItemStatus: async (id: string, status: DictStatus) => {
    const current = await requestClient.get<DictItem>(
      `/system/dict/item/${id}`,
    );
    return requestClient.put(`/system/dict/item/${id}`, {
      color: current.color,
      defaultItem: current.defaultItem,
      dictTypeId: current.dictTypeId,
      label: current.label,
      remark: current.remark ?? '',
      sort: current.sort,
      status,
      value: current.value,
    } satisfies DictItemInput);
  },
  removeItem: (id: string) => requestClient.delete(`/system/dict/item/${id}`),
  options: (code: string) =>
    requestClient.get<DictOption[]>(
      `/system/dict/type/${encodeURIComponent(code)}/items`,
    ),
};
