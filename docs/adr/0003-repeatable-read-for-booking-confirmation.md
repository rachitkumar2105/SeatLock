# 0003: REPEATABLE READ for the booking-confirmation transaction

## Status
Accepted

## Decision
Run the default transaction isolation level (Postgres's default, `READ COMMITTED`) everywhere in
the codebase, **except** `BookingService.createBooking`, which is explicitly annotated
`@Transactional(isolation = Isolation.REPEATABLE_READ)`.

## Why
`READ COMMITTED` is sufficient for the seat-lock endpoint (`SeatLockService.lock`) because that
operation is a single atomic conditional `UPDATE` — there's no read-then-write gap within the
transaction for another transaction to interleave into (see ADR 0001).

`createBooking` is different: it reads N seats' current state, re-verifies each one is still locked
by the requesting user, and then writes the booking, the `booking_seats` rows, and the payment — all
in one transaction, across potentially several seats. Under `READ COMMITTED`, a concurrent
transaction could in principle change a row's committed state between two statements in this
transaction. `REPEATABLE READ` pins the transaction's snapshot at its first statement, so any
concurrent transaction that would conflict with what this one already read is forced to fail with a
serialization error (Postgres SQLSTATE `40001`) rather than let the two interleave silently.

That serialization failure is caught by `GlobalExceptionHandler.handleConcurrencyFailure` and
surfaced to the client as an ordinary `409 Conflict` — the same shape as a normal seat-already-taken
response — so no client-side special-casing is needed.

## Trade-off accepted
`REPEATABLE READ` costs more (Postgres has to track read/write sets for the duration of the
transaction and can abort transactions that would otherwise have committed under `READ COMMITTED`).
This is accepted only for the booking-confirmation path, which is low-frequency relative to
seat-lock attempts, and where correctness of a real money-adjacent write matters more than shaving
milliseconds off a rare-per-user operation.

## Evidence
`scripts/k6/seat-lock-load-test.js`, run against the local docker-compose stack with 39 concurrent
users ramped over 40s hammering a pool of 50 seats on one event:

| Metric | Result |
|---|---|
| Total lock attempts | 366 |
| Successful locks | 50 (exactly one per seat — verified against the DB: zero double-bookings) |
| Conflicts (409) | 316 |
| Unexpected errors | 0 |
| p90 / p95 / max latency | 41ms / 44.96ms / 94.95ms |

Full numbers and the exact reproduction steps are in `README.md`'s "Load testing" section.
