package com.seatlock.exception;

import org.springframework.http.HttpStatus;

/** The referenced entity doesn't exist — 404. */
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
