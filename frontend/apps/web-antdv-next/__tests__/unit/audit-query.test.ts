import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import { auditQuery } from '../../src/views/system/log/model';
describe('audit query contract', () => {
  it('encodes full wall-clock seconds and keeps explicit failure filter', () =>
    expect(
      auditQuery(
        'login-log',
        {
          username: ' alice ',
          success: 0,
          createTime: [
            dayjs('2026-10-15 10:11:12'),
            dayjs('2026-10-15 10:12:13'),
          ],
          actorUsername: 'forbidden',
          junk: 'not-sent',
        },
        2,
        20,
      ),
    ).toEqual({
      page: 2,
      pageSize: 20,
      sortBy: 'createTime',
      sortOrder: 'desc',
      username: 'alice',
      success: 0,
      startTime: '2026-10-15 10:11:12',
      endTime: '2026-10-15 10:12:13',
    }));
  it('does not send login-only fields to operation logs', () =>
    expect(
      auditQuery(
        'operation-log',
        {
          username: 'bad',
          eventType: 'LOGIN',
          actorUsername: 'bob',
          traceId: '',
        },
        1,
        20,
      ),
    ).toEqual({
      page: 1,
      pageSize: 20,
      sortBy: 'createTime',
      sortOrder: 'desc',
      actorUsername: 'bob',
    }));
});
