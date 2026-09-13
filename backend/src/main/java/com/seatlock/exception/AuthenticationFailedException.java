package com.seatlock.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller isn't (or is no longer) authenticated — invalid credentials, an expired/revoked/
 * missing refresh token, and the like — 401. Distinct from {@link UnauthorizedActionException}:
 * that one means "we know who you are, but you can't do this"; this one means "we don't know who
 * you are, or don't trust the credentials you gave us."
 */
public class AuthenticationFailedException extends ApiException {
    public AuthenticationFailedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
