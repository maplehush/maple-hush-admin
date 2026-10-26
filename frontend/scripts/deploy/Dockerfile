# syntax=docker/dockerfile:1
ARG NODE_IMAGE=node:22-bookworm-slim
ARG NGINX_IMAGE=nginx:1.30-alpine
FROM ${NODE_IMAGE} AS builder
ARG NPM_REGISTRY=https://registry.npmmirror.com
# Corepack downloads pnpm before pnpm reads the repository's .npmrc.
ENV COREPACK_NPM_REGISTRY=${NPM_REGISTRY} \
    npm_config_registry=${NPM_REGISTRY}
ENV PNPM_HOME=/pnpm PATH=/pnpm:$PATH CI=true TZ=Asia/Shanghai \
    NODE_OPTIONS=--max-old-space-size=8192
WORKDIR /app
# packageManager pins pnpm; never use an unpinned global pnpm.
RUN corepack enable
COPY . .
RUN --mount=type=cache,id=maple-hush-pnpm,target=/pnpm/store \
    pnpm install --frozen-lockfile --store-dir=/pnpm/store
# Deployment addresses stay in Nginx; all VITE settings are public.
ENV VITE_BASE=/ VITE_GLOB_API_URL=/api VITE_NITRO_MOCK=false \
    VITE_PWA=false VITE_ARCHIVER=false
RUN pnpm --filter @vben/web-antdv-next build

FROM ${NGINX_IMAGE} AS runtime
RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder /app/apps/web-antdv-next/dist/ /usr/share/nginx/html/
COPY scripts/deploy/nginx.conf /etc/nginx/templates/maple.conf.template
COPY --chmod=755 scripts/deploy/entrypoint.sh /usr/local/bin/maple-frontend
USER nginx
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=15s --retries=3 \
    CMD wget -q -T 4 -O /dev/null http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["/usr/local/bin/maple-frontend"]
