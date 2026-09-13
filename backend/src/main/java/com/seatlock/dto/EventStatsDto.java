package com.seatlock.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record EventStatsDto(
        UUID eventId,
        long totalSeats,
        long availableSeats,
        long lockedSeats,
        long bookedSeats,
        BigDecimal revenue
) {
}
