# 0004: A real `ApiException` subclass hierarchy, not static factory methods

## Status
Accepted

## Decision
`ApiException` is now an abstract base class extended by five concrete subtypes, each fixing its
own `HttpStatus` in its constructor:

- `BadRequestException` — 400
- `AuthenticationFailedException` — 401 (invalid credentials, expired/revoked/missing refresh token)
- `UnauthorizedActionException` — 403 (authenticated, but not allowed to do this specific thing)
- `ResourceNotFoundException` — 404
- `ConflictException` — 409, with `SeatConflictException` as its own narrower subtype for the
  seat-lock/booking concurrency conflicts specifically (`SeatLockService`, `BookingService`)

`GlobalExceptionHandler` is unchanged: one `@ExceptionHandler(ApiException.class)` method reads
`ex.getStatus()` and formats the response, dispatched polymorphically by Spring regardless of which
concrete subtype was actually thrown.

This replaces an earlier version of `ApiException` that was a single concrete class with static
factory methods (`ApiException.notFound(...)`, `.conflict(...)`, etc.) — functionally equivalent at
the HTTP level, but not an inheritance hierarchy at all.

## Why
Two reasons, not one:

1. **A real subtype hierarchy is more honest about what the codebase is.** SeatLock's interview/viva
   prep material (`SeatLock_OOP_OS_DBMS_Mapping.pdf`) describes this exception hierarchy — base
   type + named subclasses — as the codebase's inheritance/polymorphism example. The static-factory
   version didn't actually match that description; this does.
2. **`SeatConflictException` as its own subtype (not just `ConflictException`) makes the
   single-most-important guarantee in this project — that a seat-lock or booking conflict is a
   distinct, nameable outcome, not just "some 409"** — visible in the type system itself, not just
   in a string message. A future caller (a test, a metrics hook, a different handler) can catch or
   branch on `SeatConflictException` specifically without string-matching a message.

## Trade-off accepted
One more exception class to know about than a single-class-with-factory-methods design, and a
constructor call (`new ResourceNotFoundException(...)`) is marginally more verbose than a static
factory (`ApiException.notFound(...)`). Accepted because every call site in this codebase throws
exactly one of these five types for exactly one reason, so the extra type isn't accidental
complexity — it's naming something that was already true.

## Verification
All 15 backend tests still pass unchanged. Re-verified live against the running docker-compose
stack that each status code is unaffected by the refactor: `404` (nonexistent event), `409`
(duplicate registration), `401` (bad login), `400` (missing `Idempotency-Key` header, with a valid
token so Spring Security's own 403 doesn't mask it), and `403` (invalid CSRF token) all still
produce the same response shape as before.
