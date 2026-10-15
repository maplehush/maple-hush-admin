import type { Dayjs } from 'dayjs';

import type { LogKind } from '#/api/system/log';
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
      ? ['username', 'eventType', 'success', 'traceId']
      : ['actorUsername', 'operationCode', 'success', 'traceId'];
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
