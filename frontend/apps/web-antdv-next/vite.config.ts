import process from 'node:process';

import { defineConfig } from '@vben/vite-config';
export default defineConfig(async () => ({
  application: {},
  vite: {
    cacheDir: process.env.VITE_CACHE_DIR,
    server: {
      proxy: {
        '/api': {
          changeOrigin: true,
          target: process.env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080',
        },
      },
    },
  },
}));
