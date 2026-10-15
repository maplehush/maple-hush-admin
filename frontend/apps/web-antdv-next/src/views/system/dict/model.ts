import type { DictItemInput } from '#/api/system/dict';
export function dictItemPayload(form: DictItemInput): DictItemInput {
  const color = form.color?.trim() || null;
  if (color && !/^#[0-9a-f]{6}$/i.test(color)) {
    throw new Error('颜色必须为 #RRGGBB 或留空');
  }
  if (!form.label.trim() || !form.value.trim()) {
    throw new Error('字典标签和值不能为空');
  }
  return {
    dictTypeId: form.dictTypeId,
    label: form.label.trim(),
    value: form.value.trim(),
    color: color?.toLowerCase() || null,
    sort: form.sort,
    status: form.status,
    defaultItem: form.defaultItem,
    remark: form.remark,
  };
}
/** 按相对亮度比较黑白文本对比度，颜色为空时交给默认主题。 */
export function colorText(color: null | string): string | undefined {
  if (!color || !/^#[0-9a-f]{6}$/i.test(color)) {
    return undefined;
  }
  const channels = [1, 3, 5].map((i) => {
    const c = Number.parseInt(color.slice(i, i + 2), 16) / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  const luminance =
    0.2126 * channels[0]! + 0.7152 * channels[1]! + 0.0722 * channels[2]!;
  return (luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05)
    ? '#000000'
    : '#ffffff';
}
