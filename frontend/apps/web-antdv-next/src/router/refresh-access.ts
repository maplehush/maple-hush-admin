import { useAccessStore } from '@vben/stores';

import { resetRoutes, router } from './index';

/** Reset dynamic routes and let the router guard fetch the current session's permissions. */
export async function refreshAccessRoutes() {
  const access = useAccessStore();
  const current = router.currentRoute.value;
  const location = {
    hash: current.hash,
    path: current.path,
    query: current.query,
  };

  access.setIsAccessChecked(false);
  resetRoutes();
  await router.replace(location);

  if (!access.isAccessChecked) {
    throw new Error('Could not refresh the current access routes.');
  }
}
