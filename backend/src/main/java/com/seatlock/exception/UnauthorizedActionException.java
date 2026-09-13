package com.seatlock.exception;

import org.springframework.http.HttpStatus;

/** The caller is authenticated but isn't allowed to perform this specific action — 403. */
public class UnauthorizedActionException extends ApiException {
    public UnauthorizedActionException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
