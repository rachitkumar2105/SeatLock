# SeatLock

Real-time seat & ticket booking platform. Built phase-by-phase per `SeatLock_Technical_Blueprint.pdf`.

## Status: Phase 6 complete (Weeks 1-8 of the roadmap) — MVP through Tier 2 done

[![CI](https://github.com/rachitkumar2105/SeatLock/actions/workflows/ci.yml/badge.svg)](https://github.com/rachitkumar2105/SeatLock/actions/workflows/ci.yml)

**Phase 1**
- Spring Boot 4 skeleton (Java 21, Maven wrapper)
- PostgreSQL + Flyway (`users`, `refresh_tokens`, `events` tables)
- JWT access tokens + httpOnly/Secure/SameSite refresh-token cookies, with rotation and a
  double-submit CSRF cookie
- Role-based access control (`USER`, `ORGANIZER`, `ADMIN`) enforced with `@PreAuthorize`
- Organizer event CRUD (create / update / publish)

**Phase 2**
- `seats`, `bookings`, `booking_seats`, `payments` tables (Flyway `V2`)
- Seat locking as a single atomic conditional `UPDATE ... WHERE status = 'AVAILABLE'` (not
  `SELECT ... FOR UPDATE`, not an in-memory lock) — Postgres serializes the race, so exactly one
  concurrent request wins and the rest get `409 Conflict` immediately
- `@Scheduled` lock-expiry job reverting abandoned locks back to `AVAILABLE` every 20s
- Booking confirmation flips locked seats to `BOOKED` inside one transaction; a mock payment
  record is created alongside it
- Idempotency-Key support on `POST /api/bookings` — a retried request with the same key returns
  the original booking instead of creating a duplicate
- **The concurrency test**: 25 users hit `POST /api/seats/{id}/lock` for the same seat at the same
  instant (synchronized via `CountDownLatch`); exactly one gets `200`, the other 24 get `409`

**Phase 3**
- Spring WebSocket/STOMP over SockJS (`/ws`) broadcasting seat status changes to
  `/topic/events/{eventId}/seats`, published only after the enclosing DB transaction commits
  (`SeatBroadcastPublisher`, `@TransactionalEventListener(phase = AFTER_COMMIT)`)
- Full Next.js 16 + TypeScript + Tailwind frontend: browse events, event details with an
  organizer seat-layout generator, a live seat map, checkout with a mock payment step and
  idempotency-key retry safety, and a My Bookings page with cancellation
- JWT access token kept in memory only (never localStorage); silent session restore on page
  load via the httpOnly refresh cookie
- **Verified live**: two browser tabs open on the same event's seat map; locking a seat in one
  tab updates the other tab's seat map instantly with no refresh, including the per-seat lock
  countdown timer — the Phase 3 "done" criterion from the roadmap

**Phase 4**
- **Organizer Dashboard** (`/dashboard`): every event the organizer owns (any status, not just
  published), with per-event occupancy/revenue stats (`GET /api/events/{id}/stats`) and a
  one-click publish action for drafts
- **Admin Dashboard** (`/admin`): platform-wide metrics, a user list with inline role
  management (`PATCH /api/admin/users/{id}/role`), and event moderation
  (`POST /api/admin/events/{id}/cancel`) — all gated server-side with `@PreAuthorize`, not just
  hidden client-side
- Skeleton loaders on the events list and seat map instead of blank/plain-text loading states
- Mobile-responsive nav (hamburger menu below the `md` breakpoint) and a seat map that stays
  usable at 375px — checked live in the browser at that width, not just by class name

**Phase 5**
- **Redis seat-map cache**: `GET /api/events/{id}/seats` (the hottest read) is `@Cacheable`, with a
  30s TTL as a backstop and *explicit* eviction on every seat-status change, fired from the single
  funnel point already used for broadcasting (`SeatBroadcastPublisher`) — so a lock/release/booking
  is reflected on the very next read, not delayed until the TTL expires
- **Redis-backed rate limiting** (fixed-window `INCR`+`EXPIRE`) on login (per IP), seat-lock, and
  booking (per user), returning `429` with `Retry-After`
- **Graceful Redis degradation, verified live, not just claimed**: a `CachingConfigurer` error
  handler makes a cache failure fall through to Postgres instead of throwing; the rate limiter
  fails open. Manually stopped the Redis container against a running instance and confirmed the
  seat-map read, login, and lock endpoints all kept returning `200` — slower (≈0.4s vs ≈0.2s with
  the timeout tightened to 300ms; it was a rough 2s before that fix), not broken
- **Readiness deliberately excludes Redis**: `management.endpoint.health.group.readiness` includes
  `db` but not `redis` — Redis is still visible as its own component on the plain
  `/actuator/health` for human debugging, but a load balancer polling *readiness* keeps routing
  traffic during a Redis outage instead of wrongly pulling a healthy instance out of rotation
- Structured JSON logs (`logstash-logback-encoder`) with a per-request correlation ID
  (`X-Request-ID`, propagated via MDC and echoed back in the response header)
- Prometheus metrics at `/actuator/prometheus`, including a custom `seatlock.lock.attempts`
  counter tagged by outcome (`success`/`conflict`) — the metric the blueprint specifically calls
  out as worth having, verified live by triggering a real lock conflict and reading it back
- Optional Prometheus + Grafana containers (`docker compose --profile observability up -d`) — not
  started by default, and no dashboards were built in Grafana; only the scrape target
  (`observability/prometheus.yml`) is wired up

Integration tests: full auth flow (register → login → refresh → logout, including refresh-token
rotation and reuse detection), event flow (organizer creates + publishes, a plain user is
forbidden), booking flow (lock → book → idempotent retry, booking without a lock is rejected,
release-then-relock by another user), the seat-lock concurrency test above, the admin/dashboard
flow (organizer sees own events + stats, a non-owner is forbidden, admin lists users/updates
roles/moderates events/reads metrics, a plain user is forbidden from admin endpoints), the seat-map
cache (a lock is visible on the very next read despite caching), and the rate limiter (exceeding
the login limit returns 429 with `Retry-After`).

**Phase 6**
- **`docker compose up` brings up the entire system from a clean clone** — Postgres, Redis, the
  Spring Boot backend, and the Next.js frontend, all containerized (`backend/Dockerfile`,
  `frontend/Dockerfile`, both multi-stage builds). Verified for real: `docker compose down` (full
  teardown) → `docker compose up -d --build` → all four containers report healthy → registered a
  user, logged in, and browsed events entirely through the built (non-dev) frontend talking to the
  built backend jar
- **GitHub Actions CI** (`.github/workflows/ci.yml`), three jobs: backend build+test (Postgres and
  Redis as service containers), frontend lint+build, and a `docker compose up` smoke test that
  builds the real images, waits for the backend to report ready, and hits register/login against
  the live containerized stack before tearing down
- Fixed the last **4 real lint errors** surfaced by Next.js 16's stricter `react-hooks` rules
  while wiring up the `frontend-build` CI job (a function called before its lexical declaration in
  two pages, and a `setState`-in-effect false-positive on the standard fetch-on-mount idiom in
  three places — see the honesty notes below) plus a genuine one in `SeatCell`'s countdown timer,
  restructured so the derived value is computed in render and the effect only drives the tick

**What Phase 6 explicitly does *not* include** (per the blueprint's own instruction never to claim
Tier 3/unbuilt work): no actual cloud deployment — see "Deploying it for real" below for what that
would take — and no demo recording. Both are legitimate to add later; neither is done today.

Integration tests: full auth flow (register → login → refresh → logout, including refresh-token
rotation and reuse detection), event flow (organizer creates + publishes, a plain user is
forbidden), booking flow (lock → book → idempotent retry, booking without a lock is rejected,
release-then-relock by another user), the seat-lock concurrency test above, the admin/dashboard
flow (organizer sees own events + stats, a non-owner is forbidden, admin lists users/updates
roles/moderates events/reads metrics, a plain user is forbidden from admin endpoints), the seat-map
cache (a lock is visible on the very next read despite caching), and the rate limiter (exceeding
the login limit returns 429 with `Retry-After`).

## Running locally

**Option A — the whole stack, one command** (what CI's smoke test does):
```bash
docker compose up -d --build
```
Brings up Postgres, Redis, the backend (`http://localhost:8090`), and the frontend
(`http://localhost:3100`). First build takes a few minutes; subsequent ones are cached.

**Option B — infra in Docker, app code on the host** (faster edit/reload loop during development):

1. Start Postgres and Redis:
   ```bash
   docker compose up -d postgres redis
   ```
   (Postgres maps to host port **5433**, Redis to **6380** — both non-default, chosen to avoid
   clashing with locally installed services.) Redis is optional at runtime — the backend starts
   and serves correctly without it, just without caching or rate limiting.

2. Run the backend (defaults to port **8090**, not 8080 — see "Known environment quirks" below):
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

3. Run the frontend (defaults to port **3100**, not 3000):
   ```bash
   cd frontend
   npm install
   cp .env.example .env.local   # already points at localhost:8090
   npm run dev -- -p 3100
   ```

4. Run the backend tests (spins up nothing extra — they hit the same docker-compose Postgres):
   ```bash
   cd backend
   ./mvnw test
   ```

## Deploying it for real

Not done — this is what it would take, per the blueprint's own recommendation (Render/Railway free
tier, or a single small VM running the Compose stack), rather than pretending it's live:

- Push `backend` and `frontend` as two services (Render/Railway both build straight from a
  Dockerfile) plus a managed Postgres add-on and a managed Redis add-on.
- Set real values for `JWT_SECRET`, `DB_*`, `REDIS_*`, and `CORS_ALLOWED_ORIGINS` (the frontend's
  real domain) as platform secrets/env vars — never the dev defaults baked into
  `application.yml`.
- Rebuild the frontend image with `NEXT_PUBLIC_API_BASE_URL`/`NEXT_PUBLIC_WS_URL` pointed at the
  backend's real deployed URL — these are baked in at build time (see `frontend/Dockerfile`), so
  they can't be swapped via runtime env vars after the image is built.
- Terminate TLS at the platform's edge (both Render and Railway do this automatically) so the
  `Secure` cookie flags `CookieUtil` already sets actually mean something in production.

## Known environment quirks (and real bugs found along the way)

Per the blueprint's advice to keep a running note of every real bug hit — this list is often more
interview-useful than the feature list itself.

- **Docker Desktop port conflicts on Windows.** Both port 8080 and 5432 turned out to already be
  claimed by unrelated Docker/WSL infrastructure on the dev machine, not by anything in this repo.
  Backend now defaults to **8090**, frontend dev server to **3100**, Postgres to **5433**.
- **`TaskStop`/process-stop on a `spring-boot:run` Maven process doesn't always kill the
  underlying JVM** in this environment — the wrapper shell exits while the forked Spring Boot
  process keeps listening on its port. If a restart fails with "port already in use" immediately
  after a supposed stop, find and kill the actual `java.exe` PID bound to the port, not just the
  Maven wrapper process.
- **Real bug: CSRF cookie `Path` was too narrow.** `CookieUtil` originally scoped both the
  httpOnly `refresh_token` cookie and the readable `csrf_token` cookie to `Path=/api/auth`. That's
  correct for the refresh token (minimize where it's sent) but wrong for the CSRF cookie — a
  cookie's `Path` also restricts which pages can *read* it via `document.cookie`, so the frontend
  could never see the CSRF token outside `/api/auth`-prefixed pages, breaking silent session
  restore on every other route. Fixed by giving the CSRF cookie `Path=/` while keeping the refresh
  cookie scoped to `/api/auth`.
- **Real bug: CORS `allowedHeaders` didn't include `X-CSRF-Token`.** The double-submit CSRF header
  was being rejected at the preflight stage with a generic "Invalid CORS request" — easy to
  misdiagnose as a cookie or auth bug rather than a CORS config gap. Fixed in `SecurityConfig`.
- **Real bug: stale closure in the WebSocket subscription hook.** `useSeatWebSocket` subscribes
  once per `eventId` and never recreates its `onMessage` callback, so it closed over `user` from
  the very first render (often still `undefined`, before auth hydration finished) — meaning the
  "someone else just selected/booked this seat" toast fired for your *own* actions too, forever,
  even after you logged in. Fixed by reading the current user id through a `ref` updated in a
  separate effect instead of the closed-over variable.
- **Spring Boot 4 module reshuffle** (this project started after Boot 3.x aged out of Spring
  Initializr): `TestRestTemplate` moved to `org.springframework.boot:spring-boot-resttestclient`
  under package `org.springframework.boot.resttestclient`, and needs
  `@AutoConfigureTestRestTemplate` explicitly; `RestTemplateBuilder` now lives in a separate
  `spring-boot-restclient` artifact that isn't pulled in transitively.
- **Testcontainers can't reach Docker Desktop's npipe** on this machine — Docker Desktop's newer
  Windows npipe protocol wraps a CLI-only handshake that generic Docker API clients can't
  complete. The standard fix (exposing the daemon over unauthenticated TCP) was deliberately not
  applied. All integration tests instead run against the docker-compose Postgres directly.
  Resolved for CI without ever needing Testcontainers: GitHub Actions' Linux runners have native
  Docker, so `.github/workflows/ci.yml` uses plain `services:` (Postgres + Redis as sibling
  containers) instead — same effect, no npipe involved at all.
- **Real bug: the Redis cache silently never wrote anything.** `GenericJackson2JsonRedisSerializer`'s
  no-arg constructor builds its own internal `ObjectMapper` with default typing on but *without*
  the JSR-310 module, so every cache write of a `Seat` (which has an `Instant` field) threw and was
  swallowed by the graceful-degradation error handler — meaning every request looked fine but the
  cache was doing nothing at all. `docker exec ... redis-cli KEYS` showed zero keys despite heavy
  traffic; the write failure only showed up in the JSON logs as a WARN once someone looked. Fixed
  by building a custom `ObjectMapper` (JavaTimeModule registered, default typing activated to
  match what the no-arg constructor does) and passing it explicitly. A dedicated test
  (`SeatMapCacheIntegrationTest`) now asserts a lock is visible on the very next read, and a manual
  `redis-cli KEYS` check confirmed real entries after the fix.
- **Real bug: Redis health status was dragging down overall `/actuator/health`, including the
  readiness group** — which would make a load balancer or orchestrator pull a perfectly healthy
  instance out of rotation the moment Redis (not a correctness dependency) had a blip. Confirmed
  live: `readinessState` flipped to `DOWN` the moment the Redis container was stopped. Fixed by
  excluding `redis` from `management.endpoint.health.group.readiness` — it still shows up on the
  plain `/actuator/health` response for human debugging, just doesn't gate traffic routing.
- **The Redis command/connect timeout was originally 2s**, meaning every request that touched the
  cache or rate limiter during a real Redis outage blocked for up to 2 full seconds before falling
  through — technically "not broken" but not honestly "slower" either. Tightened to 300ms; a live
  re-test after the fix showed request latency during an outage drop from ~2.1s to ~0.4s.
