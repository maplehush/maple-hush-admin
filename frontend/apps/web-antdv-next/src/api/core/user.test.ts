import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  changePasswordApi,
  getCurrentUserApi,
  updateAvatarApi,
  updateProfileApi,
} from './user';

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
  upload: vi.fn(),
}));

vi.mock('#/api/request', () => ({
  requestClient: mocks,
}));

describe('current user API', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('reads the unmodified current user for the profile form', async () => {
    mocks.get.mockResolvedValue({ avatar: null, roles: ['user'] });
    await expect(getCurrentUserApi()).resolves.toEqual({
      avatar: null,
      roles: ['user'],
    });
    expect(mocks.get).toHaveBeenCalledWith('/user/info');
  });

  it('uses the self-service endpoints without a target user ID', async () => {
    const profile = { realName: '张三', introduction: '简介' };
    const password = {
      oldPassword: 'old-pass-123',
      newPassword: 'new-pass-456',
    };
    const file = new File(['image'], 'photo.png', { type: 'image/png' });

    await updateProfileApi(profile);
    await changePasswordApi(password);
    await updateAvatarApi(file);

    expect(mocks.put).toHaveBeenNthCalledWith(1, '/user/profile', profile);
    expect(mocks.put).toHaveBeenNthCalledWith(2, '/user/password', password);
    expect(mocks.upload).toHaveBeenCalledWith('/user/avatar', { file });
  });
});
