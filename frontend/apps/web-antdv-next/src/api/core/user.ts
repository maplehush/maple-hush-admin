import { preferences } from '@vben/preferences';

import { requestClient } from '#/api/request';
interface CurrentUser {
  userId: string;
  username: string;
  realName: string;
  avatar: null | string;
  introduction: null | string;
  roles: string[];
  homePath: string;
}
/** 保留后端字段含义，不从用户资料生成认证 token。 */
export async function getUserInfoApi() {
  const data = await requestClient.get<CurrentUser>('/user/info');
  return {
    ...data,
    avatar: data.avatar || preferences.app.defaultAvatar,
    introduction: data.introduction || '',
  };
}
