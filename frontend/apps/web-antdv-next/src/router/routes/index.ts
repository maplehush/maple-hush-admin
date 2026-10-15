import type { RouteRecordRaw } from 'vue-router';

import { coreRoutes, fallbackNotFoundRoute } from './core';
const routes: RouteRecordRaw[] = [...coreRoutes, fallbackNotFoundRoute];
const coreRouteNames = ['Authentication', 'Login', 'InitialPassword'];
const accessRoutes: RouteRecordRaw[] = [];
export { accessRoutes, coreRouteNames, routes };
