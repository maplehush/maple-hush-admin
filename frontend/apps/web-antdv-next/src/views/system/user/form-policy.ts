interface UserForm {
  username: string;
  name: string;
  deptId?: null | string;
  remark: string;
  status: number;
  roleIds: string[];
  [key: string]: unknown;
}
export interface PendingImage {
  uploadId: string;
  expiresAt: number;
}
export function pendingImageFromAvatar(
  files?: readonly { uid: string; status?: string; response?: unknown }[],
): PendingImage | undefined {
  if (!files?.length) return undefined;
  if (files.length !== 1) throw new Error('头像只能上传一张图片');
  const file = files[0]!;
  if (file.status !== 'done')
    throw new Error('请先完成头像上传，或移除失败的文件');
  if (file.uid === 'existing-avatar') return undefined;
  const response = file.response;
  if (
    !response ||
    typeof response !== 'object' ||
    !('uploadId' in response) ||
    typeof response.uploadId !== 'string' ||
    !('expiresAt' in response) ||
    typeof response.expiresAt !== 'number'
  ) {
    throw new Error('头像上传结果无效，请重新上传');
  }
  return { uploadId: response.uploadId, expiresAt: response.expiresAt };
}
export function departmentIdFromTreeEvent(event: unknown): string | undefined {
  if (event === null || event === undefined) return undefined;
  if (typeof event !== 'object' || !('value' in event))
    throw new Error('无效的部门选择事件');
  const value = event.value;
  if (
    !value ||
    typeof value !== 'object' ||
    !('id' in value) ||
    typeof value.id !== 'string'
  )
    throw new Error('部门ID须为字符串');
  return validateId(value.id);
}
export function validateId(value: string) {
  if (!/^[1-9]\d{0,18}$/.test(value) || BigInt(value) > 9223372036854775807n)
    throw new Error('ID 须为 Long 范围内的正整数字符串');
  return value;
}
export function parseRoleIds(value: string) {
  const values = value.trim() ? value.trim().split(/[\s,，]+/) : [];
  if (values.length > 100) throw new Error('角色数量不能超过100个');
  return [...new Set(values.map(validateId))];
}
export function canManageProtectedAction(user: { superAdmin?: boolean }) {
  return user.superAdmin === false;
}
export function buildUserPayload(
  form: UserForm,
  editing: boolean,
  image?: PendingImage,
  now = Date.now(),
) {
  const name = form.name.trim();
  if (!name || name.length > 100) throw new Error('姓名必填，最多100个字符');
  if (form.remark.length > 500) throw new Error('备注最多500个字符');
  if (![0, 1].includes(form.status)) throw new Error('请选择有效状态');
  const deptId =
    !form.deptId || form.deptId === '0' ? null : validateId(form.deptId);
  const payload = {
    name,
    deptId,
    remark: form.remark,
    status: form.status as 0 | 1,
    ...(image ? { avatarUploadId: image.uploadId } : {}),
  };
  if (
    image &&
    (image.expiresAt <= now || !/^[a-f\d]{32}$/.test(image.uploadId))
  )
    throw new Error('头像已过期或无效，请重新上传');
  if (editing) return payload;
  const username = form.username.trim();
  if (!username || username.length > 64)
    throw new Error('账号必填，最多64个字符');
  return {
    ...payload,
    username,
    roleIds: parseRoleIds(form.roleIds.join(',')),
  };
}
