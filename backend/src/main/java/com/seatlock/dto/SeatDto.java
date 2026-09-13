package com.seatlock.dto;

import com.seatlock.entity.Seat;
import com.seatlock.entity.SeatStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SeatDto(
        UUID id,
        UUID eventId,
        String section,
        String row,
        int number,
        BigDecimal price,
        SeatStatus status,
        UUID lockedBy,
        Instant lockExpiresAt
) {
    public static SeatDto from(Seat seat) {
        return new SeatDto(
                seat.getId(),
                seat.getEventId(),
                seat.getSection(),
                seat.getRow(),
                seat.getNumber(),
                seat.getPrice(),
                seat.getStatus(),
                seat.getLockedBy(),
                seat.getLockExpiresAt()
        );
    }
}
