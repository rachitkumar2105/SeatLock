package com.seatlock.dto;

public record AuthResponse(
        String accessToken,
        long expiresInSeconds,
        UserDto user
) {
}
