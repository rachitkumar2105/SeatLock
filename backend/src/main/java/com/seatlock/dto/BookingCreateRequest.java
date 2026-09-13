package com.seatlock.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BookingCreateRequest(
        @NotNull UUID eventId,
        @NotEmpty List<UUID> seatIds
) {
}
