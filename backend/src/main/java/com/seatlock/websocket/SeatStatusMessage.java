package com.seatlock.websocket;

import com.seatlock.entity.SeatStatus;

import java.time.Instant;
import java.util.UUID;

public record SeatStatusMessage(UUID seatId, SeatStatus status, UUID lockedBy, Instant lockExpiresAt) {
}
