# 0002: JWT access token (memory-only) + httpOnly refresh cookie, over a single token

## Status
Accepted

## Decision
Split authentication into two tokens with different storage and lifetimes:
- A short-lived JWT **access token**, returned in the response body and kept only in memory on the
  client (a Zustand store, never `localStorage`).
- A long-lived, opaque **refresh token**, stored server-side (hashed) and delivered to the browser
  only as an `httpOnly`, `Secure`, `SameSite=Strict` cookie scoped to `/api/auth`.

Refresh rotates the token on every use and detects reuse of an already-rotated token (see
`AuthService.refresh`), at which point the entire session is revoked.

## Why
- An access token held only in JS memory is invisible to `document.cookie` and to `localStorage`
  reads, so a garden-variety XSS payload that can run JS still can't exfiltrate a token that
  outlives the page — it dies on refresh/close.
- The refresh token, which *is* long-lived and worth stealing, is `httpOnly` and never touchable by
  JS at all, so it isn't exposed by the same XSS class of bug.
- A single long-lived JWT with neither of these properties would combine the worst of both: a long
  useful lifetime for an attacker, and reachability from JS if XSS ever occurs.
- Rotation + reuse detection means a stolen refresh token is only useful once before it trips a
  full session revocation, bounding the damage window even for the cookie itself.

## Trade-off accepted
Two tokens instead of one means more moving parts: a `/api/auth/refresh` round trip, cookie-based
CSRF exposure on state-changing auth endpoints (mitigated with a double-submit `csrf_token` cookie
checked against an `X-CSRF-Token` header — see `AuthController`), and refresh-token bookkeeping in
Postgres. Accepted because the security property (no long-lived secret reachable from JS) is worth
the added complexity for a booking platform handling real payments.
