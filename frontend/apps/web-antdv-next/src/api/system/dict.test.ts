import { beforeEach, describe, expect, it, vi } from 'vitest';

import { dictApi } from './dict';

const mocks = vi.hoisted(() => ({
  delete: vi.fn(),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));

vi.mock('#/api/request', () => ({
  requestClient: mocks,
}));

describe('dictionary status updates', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads the latest type and only changes its status', async () => {
    mocks.get.mockResolvedValue({
      createTime: '2026-10-20 10:00:00',
      dictCode: 'status',
      id: '1',
      name: '状态',
      remark: '用于状态控制',
      status: 1,
      updateTime: '2026-10-20 10:00:00',
    });

    await dictApi.updateTypeStatus('1', 0);

    expect(mocks.get).toHaveBeenCalledWith('/system/dict/type/1');
    expect(mocks.put).toHaveBeenCalledWith('/system/dict/type/1', {
      dictCode: 'status',
      name: '状态',
      remark: '用于状态控制',
      status: 0,
    });
  });

  it('loads the latest item and preserves its non-status fields', async () => {
    mocks.get.mockResolvedValue({
      color: '#18d81b',
      createTime: '2026-10-20 10:00:00',
      defaultItem: true,
      dictTypeId: '1',
      id: '2',
      label: '已启用',
      remark: '默认展示项',
      sort: 4,
      status: 1,
      updateTime: '2026-10-20 10:00:00',
      value: '1',
    });

    await dictApi.updateItemStatus('2', 0);

    expect(mocks.get).toHaveBeenCalledWith('/system/dict/item/2');
    expect(mocks.put).toHaveBeenCalledWith('/system/dict/item/2', {
      color: '#18d81b',
      defaultItem: true,
      dictTypeId: '1',
      label: '已启用',
      remark: '默认展示项',
      sort: 4,
      status: 0,
      value: '1',
    });
  });
});
