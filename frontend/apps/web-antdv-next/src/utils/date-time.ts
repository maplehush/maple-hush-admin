import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';
dayjs.extend(customParseFormat);
export const SERVER_DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';
/** 服务端返回东八区墙钟文本。日期表单按原值展示，不经过 UTC 二次偏移。 */
export function parseServerDateTime(value: string) {
  const date = dayjs(value, SERVER_DATE_TIME_FORMAT, true);
  return date.isValid() ? date : null;
}
