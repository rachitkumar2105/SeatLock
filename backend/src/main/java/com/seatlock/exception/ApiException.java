package com.seatlock.exception;

import org.springframework.http.HttpStatus;

/**
 * Base of the API exception hierarchy. Every subtype fixes its own {@link HttpStatus} in its
 * constructor; {@link GlobalExceptionHandler} catches this base type and reads that status back
 * polymorphically, so adding a new subtype never requires touching the handler.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
