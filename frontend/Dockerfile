# --- Build stage ---
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
# npm ci refuses to run when the lockfile is missing an optional dependency variant for the
# current platform (e.g. a lockfile generated on Windows lacks the linux-musl native binaries
# this Alpine image needs) — npm install resolves those instead of failing outright.
RUN npm install

# NEXT_PUBLIC_* vars are inlined into the client bundle at build time, not read at container
# start — they have to be build args, not just runtime environment variables.
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8090
ARG NEXT_PUBLIC_WS_URL=http://localhost:8090/ws
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV NEXT_PUBLIC_WS_URL=${NEXT_PUBLIC_WS_URL}

COPY . .
RUN npm run build

# --- Run stage ---
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production

RUN addgroup -S seatlock && adduser -S seatlock -G seatlock

COPY package.json package-lock.json ./
RUN npm install --omit=dev

COPY --from=build /app/.next ./.next
COPY --from=build /app/public ./public
COPY --from=build /app/next.config.ts ./next.config.ts

USER seatlock
EXPOSE 3100
CMD ["npm", "run", "start", "--", "-p", "3100"]
