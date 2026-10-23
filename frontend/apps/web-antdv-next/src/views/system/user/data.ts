import type { DescriptionsItemType } from '@vben/common-ui';

import type { ComponentPropsMap } from '#/adapter/component';
import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';
import type { UserRecord } from '#/api/system/user';

import { h } from 'vue';

import Tag from 'antdv-next/dist/tag/index';

import { z } from '#/adapter/form';
import { getDeptList } from '#/api/system/dept';
import { $t } from '#/locales';

/**
 * 按用户新增或编辑场景配置字段及受权限限制时的占位展示。
 */
export function useFormSchema(
  options: {
    editing?: boolean;
    protected?: boolean;
    canAssign?: boolean;
    canListDept?: boolean;
    canListRole?: boolean;
    enableNeedsAssign?: boolean;
    avatarUploadProps?: () => ComponentPropsMap['Upload'];
  } = {},
): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'username',
      label: $t('system.user.loginAccount'),
      rules: z
        .string({ error: $t('system.user.errors.accountRequired') })
        .min(1, $t('system.user.errors.accountRequired'))
        .max(64, $t('system.user.errors.accountMax')),
      componentProps: { disabled: options.editing, maxlength: 64 },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('system.user.name'),
      rules: 'required',
      componentProps: { maxlength: 100 },
    },
    {
      component: options.canListDept ? 'ApiTreeSelect' : 'Input',
      componentProps: options.canListDept
        ? {
            allowClear: true,
            api: getDeptList,
            class: 'w-full',
            labelField: 'name',
            valueField: 'id',
            childrenField: 'children',
          }
        : { placeholder: $t('system.user.deptIdPlaceholder') },
      fieldName: 'deptId',
      label: $t('system.user.dept'),
    },
    ...(!options.editing && options.canAssign
      ? [
          {
            component: options.canListRole ? 'Select' : 'Input',
            componentProps: options.canListRole
              ? { mode: 'multiple', options: [], class: 'w-full' }
              : { placeholder: $t('system.user.roleIdsPlaceholder') },
            fieldName: 'roleIds',
            label: $t('system.role.name'),
            rules: options.canListRole
              ? z
                  .array(z.string())
                  .min(1, $t('system.user.errors.roleRequired'))
              : z.string().min(1, $t('system.user.errors.roleRequired')),
          } as VbenFormSchema,
        ]
      : []),
    {
      component: 'RadioGroup',
      componentProps: {
        disabled: options.protected || options.enableNeedsAssign,
        buttonStyle: 'solid',
        options: [
          { label: $t('common.enabled'), value: 1 },
          { label: $t('common.disabled'), value: 0 },
        ],
        optionType: 'button',
      },
      defaultValue: 1,
      fieldName: 'status',
      label: $t('system.user.status'),
    },
    {
      component: 'Textarea',
      componentProps: { maxlength: 500, rows: 3, showCount: true },
      fieldName: 'remark',
      label: $t('system.user.remark'),
    },
    {
      component: 'Upload',
      fieldName: 'avatar',
      label: $t('system.user.avatar'),
      formItemClass: 'items-start',
      help: $t('system.user.avatarHelp'),
      componentProps: () => ({
        accept: '.jpg,.jpeg,.png,.webp',
        maxCount: 1,
        maxSize: 2,
        multiple: false,
        listType: 'picture-card',
        showUploadList: {
          showRemoveIcon: (file) => file.uid !== 'existing-avatar',
        },
        disabled: true,
        ...options.avatarUploadProps?.(),
      }),
      renderComponentContent: () => ({
        default: () => $t('system.user.uploadAvatar'),
      }),
    },
  ];
}

/**
 * 构造用户列表筛选表单字段。
 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('system.user.name'),
    },
    {
      component: 'Select',
      componentProps: {
        allowClear: true,
        options: [
          { label: $t('common.enabled'), value: 1 },
          { label: $t('common.disabled'), value: 0 },
        ],
      },
      fieldName: 'status',
      label: $t('system.user.status'),
    },
    {
      component: 'RangePicker',
      fieldName: 'createTime',
      label: $t('system.user.createTime'),
    },
  ];
}

/**
 * 用户详情描述列表项
 * @param row 用户数据
 */
export function useDescriptionItems(row?: UserRecord): DescriptionsItemType[] {
  const enabled = row?.status === 1;
  return [
    { label: $t('system.user.loginAccount'), content: row?.username },
    { label: $t('system.user.name'), content: row?.name },
    { label: $t('system.user.id'), content: row?.id },
    { label: $t('system.user.dept'), content: row?.deptId ?? '—' },
    { label: $t('system.user.roleIds'), content: row?.roleIds?.join(', ') },
    {
      label: $t('system.user.superAdmin'),
      content: row?.superAdmin ? $t('common.yes') : $t('common.no'),
    },
    {
      label: $t('system.user.status'),
      content: () =>
        h(
          Tag,
          {
            color: enabled ? 'success' : 'error',
          },
          {
            default: () =>
              enabled ? $t('common.enabled') : $t('common.disabled'),
          },
        ),
    },
    { label: $t('system.user.createTime'), content: row?.createTime },
    { label: $t('system.user.remark'), content: row?.remark ?? '—' },
  ];
}

/**
 * 构造用户表格列及角色、部门等摘要展示。
 */
export function useColumns(): VxeTableGridColumns<UserRecord> {
  return [
    { field: 'username', title: $t('system.user.loginAccount'), width: 180 },
    {
      field: 'name',
      title: $t('system.user.name'),
      width: 200,
    },
    {
      field: 'id',
      title: $t('system.user.id'),
      width: 200,
    },
    {
      cellRender: { name: 'CellTag' },
      field: 'status',
      title: $t('system.user.status'),
      width: 100,
    },
    { field: 'deptId', title: $t('system.user.deptId'), width: 160 },
    {
      field: 'roleIds',
      title: $t('system.user.roleIds'),
      width: 180,
      formatter: ({ cellValue }) =>
        cellValue?.join(', ') || $t('system.user.unassigned'),
    },
    {
      field: 'remark',
      minWidth: 100,
      title: $t('system.user.remark'),
    },
    {
      field: 'createTime',
      title: $t('system.user.createTime'),
      width: 200,
    },
    {
      align: 'center',
      field: 'operation',
      fixed: 'right',
      slots: { default: 'action' },
      title: $t('system.user.operation'),
      width: 220,
    },
  ];
}

/**
 * 用户表单边界处理：头像上传凭据、树选择值、角色和 ID 校验及提交 DTO。
 */

/**
 * 用户表单内部模型；账号、角色仅在新增场景参与提交。
 */
interface UserForm {
  username: string;
  name: string;
  deptId?: null | string;
  remark: string;
  status: number;
  roleIds: string[];
  [key: string]: unknown;
}

/**
 * 已上传头像的短期凭据，保存用户时由后端完成绑定。
 */
export interface PendingImage {
  uploadId: string;
  expiresAt: number;
}

/**
 * 头像上传失败时映射到本地化提示的错误类别。
 */
export type AvatarUploadErrorKey =
  | 'avatarContentInvalid'
  | 'avatarStorageUnavailable'
  | 'avatarUpload';

/**
 * 将上传接口错误码转换为表单可展示的错误提示键。
 */
export function avatarUploadErrorKey(error: unknown): AvatarUploadErrorKey {
  if (!error || typeof error !== 'object' || !('response' in error))
    return 'avatarUpload';
  const response = error.response;
  if (!response || typeof response !== 'object' || !('data' in response))
    return 'avatarUpload';
  const data = response.data;
  if (!data || typeof data !== 'object' || !('error' in data))
    return 'avatarUpload';
  if (data.error === 'DEPENDENCY_UNAVAILABLE')
    return 'avatarStorageUnavailable';
  if (data.error === 'INVALID_ARGUMENT') return 'avatarContentInvalid';
  return 'avatarUpload';
}

/**
 * 从上传列表中提取新头像凭据；原头像无需重新绑定。
 */
export function pendingImageFromAvatar(
  files?: readonly { uid: string; status?: string; response?: unknown }[],
): PendingImage | undefined {
  if (!files?.length) return undefined;
  if (files.length !== 1)
    throw new Error($t('system.user.errors.avatarSingle'));
  const file = files[0]!;
  if (file.status !== 'done')
    throw new Error($t('system.user.errors.avatarPending'));
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
    throw new Error($t('system.user.errors.avatarInvalid'));
  }
  return { uploadId: response.uploadId, expiresAt: response.expiresAt };
}

/**
 * 读取部门树选择事件中的字符串 ID；清空时返回 undefined。
 */
export function departmentIdFromTreeEvent(event: unknown): string | undefined {
  if (event === null || event === undefined) return undefined;
  if (typeof event !== 'object' || !('value' in event))
    throw new Error($t('system.user.errors.deptEventInvalid'));
  const value = event.value;
  if (
    !value ||
    typeof value !== 'object' ||
    !('id' in value) ||
    typeof value.id !== 'string'
  )
    throw new Error($t('system.user.errors.idInvalid'));
  return validateId(value.id);
}

/**
 * 校验正整型字符串 ID，并确保不超过后端 long 范围。
 */
export function validateId(value: string) {
  if (!/^[1-9]\d{0,18}$/.test(value) || BigInt(value) > 9223372036854775807n)
    throw new Error($t('system.user.errors.idInvalid'));
  return value;
}

/**
 * 解析并去重角色 ID，限制数量且保持字符串精度。
 */
export function parseRoleIds(value: string) {
  const values = value.trim() ? value.trim().split(/[\s,，]+/) : [];
  if (values.length > 100) throw new Error($t('system.user.errors.roleCount'));
  return [...new Set(values.map(validateId))];
}

/**
 * 只有明确确认是普通用户时，才允许进入受保护用户操作。
 */
export function canManageProtectedAction(user: { superAdmin?: boolean }) {
  return user.superAdmin === false;
}

/**
 * 校验并构造用户 DTO，避免编辑时提交账号、角色等不可变字段。
 */
export function buildUserPayload(
  form: UserForm,
  editing: boolean,
  image?: PendingImage,
  now = Date.now(),
) {
  const name = form.name.trim();
  if (!name) throw new Error($t('system.user.errors.nameRequired'));
  if (name.length > 100) throw new Error($t('system.user.errors.nameMax'));
  if (form.remark.length > 500)
    throw new Error($t('system.user.errors.remarkMax'));
  if (![0, 1].includes(form.status))
    throw new Error($t('system.user.errors.statusInvalid'));
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
    throw new Error($t('system.user.errors.avatarExpired'));
  if (editing) return payload;
  const roleIds = parseRoleIds(form.roleIds.join(','));
  if (!roleIds.length) throw new Error($t('system.user.errors.roleRequired'));
  const username = form.username.trim();
  if (!username || username.length > 64)
    throw new Error(
      !username
        ? $t('system.user.errors.accountRequired')
        : $t('system.user.errors.accountMax'),
    );
  return {
    ...payload,
    username,
    roleIds,
  };
}
