import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';
import type { LogKind } from '#/api/system/log';
export function useSearchSchema(kind: LogKind): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: kind === 'login-log' ? 'username' : 'actorUsername',
      label: kind === 'login-log' ? '登录账号' : '操作账号',
      componentProps: { placeholder: '精确匹配', maxlength: 64 },
    },
    kind === 'login-log'
      ? {
          component: 'Select',
          fieldName: 'eventType',
          label: '事件',
          componentProps: {
            allowClear: true,
            options: ['LOGIN', 'LOGOUT', 'REFRESH'].map((value) => ({
              label: value,
              value,
            })),
          },
        }
      : {
          component: 'Input',
          fieldName: 'operationCode',
          label: '操作码',
          componentProps: { maxlength: 120, placeholder: '精确匹配' },
        },
    {
      component: 'Select',
      fieldName: 'success',
      label: '结果',
      componentProps: {
        allowClear: true,
        options: [
          { label: '成功', value: 1 },
          { label: '失败', value: 0 },
        ],
      },
    },
    {
      component: 'Input',
      fieldName: 'traceId',
      label: 'Trace ID',
      componentProps: { maxlength: 64 },
    },
    {
      component: 'RangePicker',
      fieldName: 'createTime',
      label: '发生时间（东八区）',
      componentProps: {
        showTime: { format: 'HH:mm:ss' },
        format: 'YYYY-MM-DD HH:mm:ss',
      },
    },
    {
      component: 'Select',
      fieldName: 'sortBy',
      label: '排序字段',
      defaultValue: 'createTime',
      componentProps: {
        options: [
          { label: '发生时间', value: 'createTime' },
          { label: 'ID', value: 'id' },
        ],
      },
    },
    {
      component: 'Select',
      fieldName: 'sortOrder',
      label: '排序方向',
      defaultValue: 'desc',
      componentProps: {
        options: [
          { label: '降序', value: 'desc' },
          { label: '升序', value: 'asc' },
        ],
      },
    },
  ];
}
export function useColumns(kind: LogKind): VxeTableGridColumns {
  return [
    { field: 'id', title: 'ID', width: 100 },
    {
      field: kind === 'login-log' ? 'username' : 'actorUsername',
      title: kind === 'login-log' ? '账号' : '操作人',
      minWidth: 140,
    },
    {
      field: kind === 'login-log' ? 'eventType' : 'operationCode',
      title: kind === 'login-log' ? '事件' : '操作码',
      minWidth: 160,
    },
    {
      field: 'success',
      title: '结果',
      width: 100,
      cellRender: {
        name: 'CellTag',
        options: [
          { label: '成功', value: true, color: 'success' },
          { label: '失败', value: false, color: 'error' },
        ],
      },
    },
    {
      field: kind === 'login-log' ? 'reasonCode' : 'summary',
      title: kind === 'login-log' ? '原因码' : '摘要',
      minWidth: 150,
    },
    { field: 'clientIp', title: '客户端 IP', width: 140 },
    { field: 'createTime', title: '发生时间', width: 180 },
    {
      field: 'operation',
      title: '操作',
      width: 100,
      fixed: 'right',
      slots: { default: 'action' },
    },
  ];
}
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
export const detailLabels: Record<string, string> = {
  id: 'ID',
  userId: '用户 ID',
  username: '账号',
  eventType: '事件',
  success: '结果',
  reasonCode: '原因码',
  clientIp: '客户端 IP',
  userAgent: '客户端标识',
  traceId: 'Trace ID',
  createTime: '发生时间',
  actorUserId: '操作人 ID',
  actorUsername: '操作账号',
  operationCode: '操作码',
  targetType: '目标类型',
  targetId: '目标 ID',
  summary: '摘要',
  requestMethod: '请求方法',
  requestPath: '路由模板',
};
