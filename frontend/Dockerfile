# syntax=docker/dockerfile:1.7

# ---- deps: install exactly what the lockfile says ---------------------------------------------
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

# ---- build: type-check and bundle --------------------------------------------------------------
FROM deps AS build
COPY . .
RUN npm run build

# ---- test: `docker build --target test ./frontend` runs lint and unit tests ------------------
FROM deps AS test
COPY . .
RUN npm run lint && npm run typecheck && npm test

# ---- e2e: Playwright with browsers preinstalled (used by docker-compose.test.yml) -------------
FROM mcr.microsoft.com/playwright:v1.49.1-noble AS e2e
WORKDIR /e2e
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY playwright.config.ts tsconfig.json tsconfig.node.json ./
COPY e2e ./e2e
CMD ["npx", "playwright", "test"]

# ---- runtime: unprivileged nginx serving static files and proxying /api -----------------------
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
# Rendered with envsubst at startup into /etc/nginx/conf.d/default.conf
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV API_UPSTREAM=http://api:8080
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
