# API PR4Y — build limpio para Railway (incluye public/requests, public/content, user prefs)
FROM node:20-alpine AS base
RUN corepack enable && corepack prepare pnpm@8.15.0 --activate
WORKDIR /app

FROM base AS builder
COPY . .
RUN pnpm install --no-frozen-lockfile && pnpm --filter @pr4y/api build

FROM base AS runner
ENV NODE_ENV=production
WORKDIR /app
COPY --from=builder /app/package.json /app/pnpm-lock.yaml /app/pnpm-workspace.yaml ./
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/apps/api ./apps/api
COPY --from=builder /app/packages ./packages
EXPOSE 8080
CMD ["pnpm", "--filter", "@pr4y/api", "start"]
