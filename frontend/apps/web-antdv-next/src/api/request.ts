import { preferences } from '@vben/preferences';
import {
  defaultResponseInterceptor,
  errorMessageResponseInterceptor,
  RequestClient,
} from '@vben/request';

import { message } from 'antdv-next';

import { session } from '#/auth/session';
import { SessionChangedError } from '#/auth/session-coordinator';
import { $t } from '#/locales';

import { resolveApiErrorMessage } from './error-message';
// 平台 API 固定同源 /api，认证 cookie 不发送到外部地址。
export const requestClient = new RequestClient({
  baseURL: '/api',
  responseReturn: 'data',
});
export const baseRequestClient = new RequestClient({ baseURL: '/api' });
requestClient.addRequestInterceptor({
  fulfilled: (config) => {
    const state = session.state;
    const metadata = config as typeof config & { sessionEpoch?: string };
    metadata.sessionEpoch ??= state.epoch;
    if (metadata.sessionEpoch !== state.epoch) {
      throw new SessionChangedError($t('errors.sessionChanged'));
    }
    config.headers.Authorization = state.token
      ? `Bearer ${state.token}`
      : undefined;
    config.headers['Accept-Language'] = preferences.app.locale;
    return config;
  },
});
requestClient.addResponseInterceptor(
  defaultResponseInterceptor({
    codeField: 'code',
    dataField: 'data',
    successCode: 0,
  }),
);
requestClient.addResponseInterceptor({
  rejected: async (error) => {
    const config = error.config;
    if (error.response?.status !== 401 || !config) {
      throw error;
    }
    const epoch = config.sessionEpoch as string;
    if (config.sessionRetried || !session.state.token) {
      session.invalidate(epoch);
      throw error;
    }
    const failed = String(config.headers.Authorization || '').replace(
      /^Bearer /,
      '',
    );
    await session.refresh(failed, epoch);
    config.sessionRetried = true;
    return requestClient.request(config.url, config);
  },
});
requestClient.addResponseInterceptor(
  errorMessageResponseInterceptor((_text, error) => {
    if (error instanceof SessionChangedError) {
      message.error($t('errors.sessionChanged'));
      return;
    }
    message.error(
      resolveApiErrorMessage(error?.response?.data, preferences.app.locale, $t),
    );
  }),
);
