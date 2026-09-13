package com.seatlock.dto;

import com.seatlock.entity.RefreshToken;

import java.time.Instant;
import java.util.UUID;

public record SessionDto(
        UUID id,
        String userAgent,
        String ipAddress,
        Instant lastUsedAt,
        Instant createdAt,
        Instant expiresAt
) {
    public static SessionDto from(RefreshToken token) {
        return new SessionDto(
                token.getId(),
                token.getUserAgent(),
                token.getIpAddress(),
                token.getLastUsedAt(),
                token.getCreatedAt(),
                token.getExpiresAt()
        );
    }
}
