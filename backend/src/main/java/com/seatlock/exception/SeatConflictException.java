package com.seatlock.exception;

/**
 * A seat-lock or seat-booking attempt lost the race to another concurrent request — still a 409,
 * via {@link ConflictException}, but its own type so this specific, interview-relevant case (see
 * {@code SeatLockService} and {@code BookingService}) is distinguishable from an ordinary
 * business-state conflict if it's ever caught or handled differently later.
 */
public class SeatConflictException extends ConflictException {
    public SeatConflictException(String message) {
        super(message);
    }
}
