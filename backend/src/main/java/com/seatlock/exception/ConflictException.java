package com.seatlock.exception;

import org.springframework.http.HttpStatus;

/**
 * The request conflicts with the current state of the resource — 409. General business-state
 * conflicts (an event that's already published, an email already registered, a booking that's
 * already cancelled) throw this directly; {@link SeatConflictException} is the narrower subtype
 * for the seat-lock/booking concurrency conflicts specifically — the one place in this codebase
 * where a 409 is the core correctness guarantee, not just a validation nicety.
 */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
