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
    throw new Error(
      realName
        ? 'page.profile.errors.nameMax'
        : 'page.profile.errors.nameRequired',
    );
  }
  if (introduction.length > 500) {
    throw new Error('page.profile.errors.introductionLength');
  }
  return { realName, introduction };
}

export function passwordPayload(values: PasswordInput) {
  const { oldPassword, newPassword, confirmPassword } = values;
  if (typeof oldPassword !== 'string' || !oldPassword) {
    throw new Error('page.profile.errors.oldPasswordRequired');
  }
  if (
    typeof newPassword !== 'string' ||
    newPassword.length < 8 ||
    newPassword.length > 128
  ) {
    throw new Error('page.profile.errors.passwordLength');
  }
  if (new TextEncoder().encode(newPassword).length > 72) {
    throw new Error('page.profile.errors.passwordBytes');
  }
  if (newPassword !== confirmPassword) {
    throw new Error('page.profile.errors.passwordMismatch');
  }
  return { oldPassword, newPassword };
}
