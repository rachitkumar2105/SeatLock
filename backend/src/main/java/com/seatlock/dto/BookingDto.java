package com.seatlock.dto;

import com.seatlock.entity.Booking;
import com.seatlock.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingDto(
        UUID id,
        UUID eventId,
        BookingStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        List<UUID> seatIds
) {
    public static BookingDto from(Booking booking, List<UUID> seatIds) {
        return new BookingDto(
                booking.getId(),
                booking.getEventId(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getCreatedAt(),
                seatIds
        );
    }
}
