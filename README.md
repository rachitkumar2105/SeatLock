# SeatLock

Real-time seat & ticket booking platform. Built phase-by-phase per `SeatLock_Technical_Blueprint.pdf`.

## Status: Phase 4 complete (Weeks 1-6 of the roadmap)

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

Integration tests: full auth flow (register → login → refresh → logout, including refresh-token
rotation and reuse detection), event flow (organizer creates + publishes, a plain user is
forbidden), booking flow (lock → book → idempotent retry, booking without a lock is rejected,
release-then-relock by another user), the seat-lock concurrency test above, and the admin/
dashboard flow (organizer sees own events + stats, a non-owner is forbidden, admin lists users/
updates roles/moderates events/reads metrics, a plain user is forbidden from admin endpoints).

Not yet built: Redis/rate limiting/observability (Phase 5), CI/deployment (Phase 6).

## Running locally

1. Start Postgres:
   ```bash
   docker compose up -d postgres
   ```
   (Maps to host port **5433**, not 5432 — chosen to avoid clashing with a locally installed
   Postgres service. See `docker-compose.yml` / `backend/src/main/resources/application.yml`.)

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
  applied. All integration tests instead run against the docker-compose Postgres directly. This
  should be revisited before attempting a Testcontainers-based CI pipeline in Phase 6.
