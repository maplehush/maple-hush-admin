import { preferences } from '@vben/preferences';

import { requestClient } from '#/api/request';
export interface CurrentUser {
  userId: string;
  username: string;
  realName: string;
  avatar: null | string;
  introduction: null | string;
  roles: string[];
  homePath: string;
}
export interface ProfilePayload {
  realName: string;
  introduction: string;
}
export interface PasswordPayload {
  oldPassword: string;
  newPassword: string;
}
export const getCurrentUserApi = () =>
  requestClient.get<CurrentUser>('/user/info');
export const updateProfileApi = (data: ProfilePayload) =>
  requestClient.put<Pick<CurrentUser, 'introduction' | 'realName' | 'userId'>>(
    '/user/profile',
    data,
  );
export const changePasswordApi = (data: PasswordPayload) =>
  requestClient.put<null>('/user/password', data);
export const updateAvatarApi = (file: File) =>
  requestClient.upload<{ avatar: string }>('/user/avatar', { file });
/** 保留后端字段含义，不从用户资料生成认证 token。 */
export async function getUserInfoApi() {
  const data = await getCurrentUserApi();
  return {
    ...data,
    avatar: data.avatar || preferences.app.defaultAvatar,
    introduction: data.introduction || '',
  };
}
