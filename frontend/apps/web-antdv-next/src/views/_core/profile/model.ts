interface ProfileInput {
  realName?: unknown;
  introduction?: unknown;
  [key: string]: unknown;
}

interface PasswordInput {
  oldPassword?: unknown;
  newPassword?: unknown;
  confirmPassword?: unknown;
  [key: string]: unknown;
}

export function profilePayload(values: ProfileInput) {
  const realName = String(values.realName ?? '').trim();
  const introduction = String(values.introduction ?? '').trim();
  if (!realName || realName.length > 100) {
    throw new Error('姓名不能为空且最多 100 个字符');
  }
  if (introduction.length > 500) {
    throw new Error('个人简介最多 500 个字符');
  }
  return { realName, introduction };
}

export function passwordPayload(values: PasswordInput) {
  const { oldPassword, newPassword, confirmPassword } = values;
  if (typeof oldPassword !== 'string' || !oldPassword) {
    throw new Error('请输入旧密码');
  }
  if (
    typeof newPassword !== 'string' ||
    newPassword.length < 8 ||
    newPassword.length > 128
  ) {
    throw new Error('新密码需为 8～128 个字符');
  }
  if (new TextEncoder().encode(newPassword).length > 72) {
    throw new Error('新密码的 UTF-8 编码不能超过 72 字节');
  }
  if (newPassword !== confirmPassword) {
    throw new Error('两次输入的新密码不一致');
  }
  return { oldPassword, newPassword };
}
