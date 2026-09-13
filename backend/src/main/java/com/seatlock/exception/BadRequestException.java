package com.seatlock.exception;

import org.springframework.http.HttpStatus;

/** The request itself is malformed or violates a precondition — 400. */
public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
