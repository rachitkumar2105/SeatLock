// Load test for the seat-lock concurrency guarantee (see docs/adr/0001, docs/adr/0003).
//
// Ramps concurrent virtual users, each repeatedly attempting to lock a randomly-chosen seat out
// of a fixed pool on the same event, and records the 409 (conflict) rate and p95/p99 latency.
// A healthy result is a nonzero conflict rate under contention (that's the locking mechanism
// working, not a bug) with latency staying low even as concurrency increases, and zero seats
// ever double-booked.
//
// Prerequisites (see README.md "Load testing" for the exact commands used to produce them): a
// published event with a seat pool, and a JSON array of pre-issued access tokens (one per virtual
// user) so the load test itself never touches the login rate limiter.
//
// Run against the local docker-compose stack:
//   docker run --rm -i --add-host=host.docker.internal:host-gateway \
//     -e BASE_URL=http://host.docker.internal:8090 \
//     -e SEAT_IDS_FILE=/seat_ids.json -e TOKENS_FILE=/tokens.json \
//     -v <scratchpad>/seat_ids.json:/seat_ids.json \
//     -v <scratchpad>/tokens.json:/tokens.json \
//     -v <this-file>:/script.js \
//     grafana/k6 run /script.js

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";
import { SharedArray } from "k6/data";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8090";

const seatIds = new SharedArray("seatIds", function () {
  return JSON.parse(open(__ENV.SEAT_IDS_FILE || "/seat_ids.json"));
});
const tokens = new SharedArray("tokens", function () {
  return JSON.parse(open(__ENV.TOKENS_FILE || "/tokens.json"));
});

export const lockSuccess = new Counter("seatlock_success");
export const lockConflict = new Counter("seatlock_conflict");
export const lockOtherError = new Counter("seatlock_other_error");
export const lockLatency = new Trend("seatlock_latency_ms", true);

export const options = {
  scenarios: {
    ramp: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "10s", target: 20 },
        { duration: "20s", target: 39 },
        { duration: "10s", target: 0 },
      ],
    },
  },
};

export default function () {
  const token = tokens[__VU % tokens.length];
  const seatId = seatIds[Math.floor(Math.random() * seatIds.length)];

  const response = http.post(`${BASE_URL}/api/seats/${seatId}/lock`, null, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status === 200) {
    lockSuccess.add(1);
  } else if (response.status === 409) {
    lockConflict.add(1);
  } else {
    lockOtherError.add(1);
  }
  lockLatency.add(response.timings.duration);

  check(response, {
    "status is 200 or 409": (r) => r.status === 200 || r.status === 409,
  });

  // Each user account is also subject to the app's own per-user seat-lock rate limit (30 req/60s
  // — see application.yml). Pace requests comfortably under that so this test measures database
  // contention, not our own rate limiter kicking in.
  sleep(2.5);
}
