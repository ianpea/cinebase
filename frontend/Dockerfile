# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Copied separately so this layer is only invalidated when dependencies change.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------------------------------------------------------------------------
# Runtime stage
#
# adapter-node emits a self-contained server in build/, and Vite inlines the
# devDependencies (bits-ui, svelte, tailwind, ...) into it. Only `dependencies`
# — currently just @urql/core — has to be installed here.
# ---------------------------------------------------------------------------
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production

# `--ignore-scripts` skips the `prepare` hook, which would try to run
# `svelte-kit sync` against source files this stage does not copy.
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts

COPY --from=build /app/build ./build

USER node
EXPOSE 3000

CMD ["node", "build"]
