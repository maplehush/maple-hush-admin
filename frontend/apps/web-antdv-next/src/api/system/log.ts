import { requestClient } from '#/api/request';
export type LogKind = 'login-log' | 'operation-log';
export interface AuditRow {
  id: string;
  createTime: string;
  success: boolean;
  traceId: string;
  [key: string]: unknown;
}
export function getAuditLogs(kind: LogKind, params: Record<string, unknown>) {
  return requestClient.get<{ items: AuditRow[]; total: number }>(
    `/system/${kind}/list`,
    { params },
  );
}
