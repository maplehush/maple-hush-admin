import type { Recordable } from '@vben/types';

import type { LoginInput } from '#/auth/session-coordinator';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { message } from 'antdv-next';
import { defineStore } from 'pinia';

import {
  changeInitialPasswordApi,
  getAccessCodesApi,
  getUserInfoApi,
  loginApi,
  logoutApi,
} from '#/api';
import { session, subscribeSession } from '#/auth/session';
import { resetRoutes } from '#/router';
import { safeRedirect } from '#/utils/redirect';
export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();
  const loginLoading = ref(false);
  // 专用改密凭据只保留在内存，刷新页面后需重新登录。
  const passwordChangeToken = ref<null | string>(null);
  let observedEpoch = session.state.epoch;
  let subscribed = false;
  function clearIdentity() {
    const loading = loginLoading.value;
    resetAllStores();
    loginLoading.value = loading;
    resetRoutes();
  }
  function initializeSession() {
    clearIdentity();
    accessStore.setAccessToken(session.state.token);
    observedEpoch = session.state.epoch;
    if (subscribed) {
      return;
    }
    subscribed = true;
    subscribeSession((state, remote) => {
      const changed = state.epoch !== observedEpoch;
      observedEpoch = state.epoch;
      if (changed && remote) {
        clearIdentity();
      }
      accessStore.setAccessToken(state.token);
      if (
        !state.token &&
        !passwordChangeToken.value &&
        !router.currentRoute.value.path.startsWith('/auth')
      ) {
        clearIdentity();
        void router.replace('/auth/login');
      } else if (changed && remote && state.token) {
        void router.replace('/dashboard/workspace');
      }
    });
  }
  async function fetchUserInfo() {
    const epoch = session.state.epoch;
    const info = await getUserInfoApi();
    if (epoch !== session.state.epoch) {
      throw new Error('登录状态已变化');
    }
    userStore.setUserInfo(info);
    return info;
  }
  async function loadIdentity() {
    const epoch = session.state.epoch;
    const [info, codes] = await Promise.all([
      fetchUserInfo(),
      getAccessCodesApi(),
    ]);
    if (epoch !== session.state.epoch) {
      throw new Error('登录状态已变化');
    }
    accessStore.setAccessCodes(codes);
    return info;
  }
  async function authLogin(
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    loginLoading.value = true;
    try {
      const result = await loginApi(params as LoginInput);
      clearIdentity();
      accessStore.setAccessToken(session.state.token);
      if (result.passwordChangeRequired) {
        passwordChangeToken.value = result.passwordChangeToken;
        await router.replace('/auth/initial-password');
        return;
      }
      if (onSuccess) {
        await onSuccess();
      } else {
        await router.replace(
          safeRedirect(router.currentRoute.value.query.redirect),
        );
      }
    } finally {
      loginLoading.value = false;
    }
  }
  async function changeInitialPassword(newPassword: string) {
    if (!passwordChangeToken.value) {
      await router.replace('/auth/login');
      return;
    }
    await changeInitialPasswordApi(passwordChangeToken.value, newPassword);
    passwordChangeToken.value = null;
    message.success('密码修改成功，请使用新密码登录');
    await router.replace('/auth/login');
  }
  async function logout() {
    try {
      await logoutApi();
    } catch {
      message.warning('本地已退出，服务端退出未确认，请稍后重试');
    } finally {
      clearIdentity();
      accessStore.setAccessToken(session.state.token);
      await router.replace(
        session.state.token ? '/dashboard/workspace' : '/auth/login',
      );
    }
  }
  async function completePasswordChange() {
    // 后端已撤销所有 refresh 会话并清除 Cookie；此处只清浏览器 access。
    session.invalidate();
    clearIdentity();
    await router.replace('/auth/login');
  }
  function $reset() {
    loginLoading.value = false;
    passwordChangeToken.value = null;
  }
  return {
    $reset,
    authLogin,
    changeInitialPassword,
    completePasswordChange,
    fetchUserInfo,
    initializeSession,
    loadIdentity,
    loginLoading,
    logout,
    passwordChangeToken,
  };
});
