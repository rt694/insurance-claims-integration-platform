FROM node:24.19.0-alpine3.23 AS build

WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@11.19.0 --activate

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
ARG VITE_OIDC_AUTHORITY=http://localhost:8090/realms/insurance-platform
ARG VITE_OIDC_CLIENT_ID=claims-portal
ENV VITE_OIDC_AUTHORITY=$VITE_OIDC_AUTHORITY \
    VITE_OIDC_CLIENT_ID=$VITE_OIDC_CLIENT_ID
RUN pnpm build

FROM nginxinc/nginx-unprivileged:1.29.8-alpine3.23-slim

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 8080
