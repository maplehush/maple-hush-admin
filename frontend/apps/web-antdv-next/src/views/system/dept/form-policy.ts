import type { DeptPayload } from '#/api/system/dept';

interface TreeNode {
  id: string;
  name: string;
  children?: TreeNode[];
}
interface ParentOption {
  value: string;
  title: string;
  children: ParentOption[];
}
export function parentOptions(
  tree: TreeNode[],
  excludedId?: string,
): ParentOption[] {
  return tree
    .filter((node) => node.id !== excludedId)
    .map((node) => ({
      value: node.id,
      title: node.name,
      children: parentOptions(node.children ?? [], excludedId),
    }));
}
export function buildDeptPayload(form: DeptPayload): DeptPayload {
  const name = form.name.trim();
  if (!name || name.length > 100)
    throw new Error('部门名称必填，最多100个字符');
  const pid = form.pid || '0';
  if (!/^(?:0|[1-9]\d{0,18})$/.test(pid) || BigInt(pid) > 9223372036854775807n)
    throw new Error('请选择有效的父部门');
  if (
    !Number.isInteger(form.sort) ||
    form.sort < -2147483648 ||
    form.sort > 2147483647
  )
    throw new Error('排序须为32位整数');
  if (![0, 1].includes(form.status)) throw new Error('请选择有效状态');
  if (form.remark.length > 500) throw new Error('备注最多500个字符');
  return {
    pid,
    name,
    sort: form.sort,
    status: form.status,
    remark: form.remark,
  };
}
