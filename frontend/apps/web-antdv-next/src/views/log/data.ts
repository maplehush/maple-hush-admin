import type { Dayjs } from 'dayjs';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';
import type { LogKind } from '#/api/system/log';

import { $t } from '#/locales';

/**
 * 将登录事件和结果原因转换为当前语言；未知编码原样显示，便于识别新事件。
 */
export function loginLogText(
  type: 'events' | 'reasons',
  value: unknown,
): string {
  const code = String(value ?? '');
  const codes =
    type === 'events'
      ? ['LOGIN', 'LOGOUT', 'REFRESH']
      : [
          'OK',
          'PASSWORD_CHANGE_REQUIRED',
          'CAPTCHA_INVALID',
          'UNAUTHENTICATED',
          'RATE_LIMITED',
          'DEPENDENCY_UNAVAILABLE',
          'SESSION_INVALID',
          'REFRESH_REPLAY',
        ];
  return codes.includes(code) ? $t(`system.log.${type}.${code}`) : code || '—';
}

/**
 * 按日志类型生成对应的筛选表单。
 */
export function useSearchSchema(kind: LogKind): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: kind === 'login-log' ? 'username' : 'actorUsername',
      label:
        kind === 'login-log'
          ? $t('system.log.loginAccount')
          : $t('system.log.operationAccount'),
      componentProps: {
        placeholder: $t('system.log.exactMatch'),
        maxlength: 64,
      },
    },
    kind === 'login-log'
      ? {
          component: 'Select',
          fieldName: 'eventType',
          label: $t('system.log.event'),
          componentProps: {
            allowClear: true,
            options: ['LOGIN', 'LOGOUT', 'REFRESH'].map((value) => ({
              label: loginLogText('events', value),
              value,
            })),
          },
        }
      : {
          component: 'Input',
          fieldName: 'operationCode',
          label: $t('system.log.operationCode'),
          componentProps: {
            maxlength: 120,
            placeholder: $t('system.log.exactMatch'),
          },
        },
    {
      component: 'Select',
      fieldName: 'success',
      label: $t('system.log.result'),
      componentProps: {
        allowClear: true,
        options: [
          { label: $t('system.log.success'), value: 1 },
          { label: $t('system.log.failure'), value: 0 },
        ],
      },
    },
    {
      component: 'RangePicker',
      fieldName: 'createTime',
      label: $t('system.log.occurredAt'),
      componentProps: {
        showTime: { format: 'HH:mm:ss' },
        format: 'YYYY-MM-DD HH:mm:ss',
      },
    },
    {
      component: 'Select',
      fieldName: 'sortBy',
      label: $t('system.log.sortField'),
      defaultValue: 'createTime',
      componentProps: {
        options: [
          { label: $t('system.log.occurredAt'), value: 'createTime' },
          { label: 'ID', value: 'id' },
        ],
      },
    },
    {
      component: 'Select',
      fieldName: 'sortOrder',
      label: $t('system.log.sortDirection'),
      defaultValue: 'desc',
      componentProps: {
        options: [
          { label: $t('system.log.descending'), value: 'desc' },
          { label: $t('system.log.ascending'), value: 'asc' },
        ],
      },
    },
  ];
}

/**
 * 按登录日志或操作日志生成列表列配置。
 */
export function useColumns(kind: LogKind): VxeTableGridColumns {
  return [
    { field: 'id', title: 'ID', width: 100 },
    {
      field: kind === 'login-log' ? 'username' : 'actorUsername',
      title:
        kind === 'login-log'
          ? $t('system.log.account')
          : $t('system.log.operator'),
      minWidth: 140,
    },
    {
      field: kind === 'login-log' ? 'eventType' : 'operationCode',
      title:
        kind === 'login-log'
          ? $t('system.log.event')
          : $t('system.log.operationCode'),
      minWidth: 160,
      formatter:
        kind === 'login-log'
          ? ({ cellValue }) => loginLogText('events', cellValue)
          : undefined,
    },
    {
      field: 'success',
      title: $t('system.log.result'),
      width: 100,
      cellRender: {
        name: 'CellTag',
        options: [
          { label: $t('system.log.success'), value: true, color: 'success' },
          { label: $t('system.log.failure'), value: false, color: 'error' },
        ],
      },
    },
    {
      field: kind === 'login-log' ? 'reasonCode' : 'summary',
      title:
        kind === 'login-log'
          ? $t('system.log.reason')
          : $t('system.log.summary'),
      minWidth: 150,
      formatter:
        kind === 'login-log'
          ? ({ cellValue }) => loginLogText('reasons', cellValue)
          : undefined,
    },
    { field: 'clientIp', title: $t('system.log.clientIp'), width: 140 },
    { field: 'createTime', title: $t('system.log.occurredAt'), width: 180 },
    {
      field: 'operation',
      title: $t('system.user.operation'),
      width: 100,
      fixed: 'right',
      slots: { default: 'action' },
    },
  ];
}

/**
 * 列出不同日志详情允许展示的字段，避免显示未声明数据。
 */
export const detailFields = {
  'login-log': [
    'id',
    'userId',
    'username',
    'eventType',
    'success',
    'reasonCode',
    'clientIp',
    'userAgent',
    'traceId',
    'createTime',
  ],
  'operation-log': [
    'id',
    'actorUserId',
    'actorUsername',
    'operationCode',
    'targetType',
    'targetId',
    'success',
    'summary',
    'requestMethod',
    'requestPath',
    'clientIp',
    'traceId',
    'createTime',
  ],
};

/**
 * 提供日志详情字段对应的本地化标签。
 */
export function detailLabels(): Record<string, string> {
  return {
    id: 'ID',
    userId: $t('system.log.userId'),
    username: $t('system.log.account'),
    eventType: $t('system.log.event'),
    success: $t('system.log.result'),
    reasonCode: $t('system.log.reasonCode'),
    clientIp: $t('system.log.clientIp'),
    userAgent: $t('system.log.clientIdentifier'),
    traceId: 'Trace ID',
    createTime: $t('system.log.occurredAt'),
    actorUserId: $t('system.log.actorUserId'),
    actorUsername: $t('system.log.operationAccount'),
    operationCode: $t('system.log.operationCode'),
    targetType: $t('system.log.targetType'),
    targetId: $t('system.log.targetId'),
    summary: $t('system.log.summary'),
    requestMethod: $t('system.log.requestMethod'),
    requestPath: $t('system.log.requestPath'),
  };
}

/**
 * 日志查询参数、详情字段和详情标签配置。
 */
export function auditQuery(
  kind: LogKind,
  values: Record<string, unknown>,
  page: number,
  pageSize: number,
) {
  const params: Record<string, unknown> = {
    page,
    pageSize,
    sortBy: values.sortBy || 'createTime',
    sortOrder: values.sortOrder || 'desc',
  };
  const fields =
    kind === 'login-log'
      ? ['username', 'eventType', 'success']
      : ['actorUsername', 'operationCode', 'success'];
  for (const key of fields) {
    const value = values[key];
    if (value !== undefined && value !== null && value !== '') {
      params[key] = typeof value === 'string' ? value.trim() : value;
    }
  }
  const range = values.createTime as [Dayjs, Dayjs] | undefined;
  if (range?.[0] && range[1]) {
    params.startTime = range[0].format('YYYY-MM-DD HH:mm:ss');
    params.endTime = range[1].format('YYYY-MM-DD HH:mm:ss');
  }
  return params;
}
