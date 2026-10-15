/** 仅允许应用内路径，拒绝外部跳转与认证页循环。 */
export function safeRedirect(value: unknown): string {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\') ||
    /[\r\n]/.test(value) ||
    value.startsWith('/auth')
  ) {
    return '/dashboard/workspace';
  }
  return value;
}
