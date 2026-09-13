# 0001: Optimistic locking for seat status transitions

## Status
Accepted

## Decision
Use a single atomic conditional `UPDATE ... WHERE status = 'AVAILABLE'` (via Spring Data JPA's
`@Modifying` query methods on `SeatRepository`) for seat lock/confirm transitions, instead of
pessimistic row locks (`SELECT ... FOR UPDATE`) or an in-memory lock map.

## Why
- Correct across multiple app instances — Postgres is the single arbiter of seat state, so there's
  no coordination problem between app replicas the way there would be with an in-memory lock map.
- No lock is held for the request duration, so throughput under contention is better: a losing
  request fails fast instead of queueing behind the winner.
- A standard, well-understood Spring Data JPA pattern — no custom locking infrastructure to
  maintain or reason about.

## Trade-off accepted
Concurrent conflicting requests fail fast (`409 Conflict`) rather than queueing. This is acceptable
here because the client can safely retry a seat-lock attempt, and a hard fail is the correct UX for
"someone else got this seat first" — queueing would just delay the same negative outcome.
