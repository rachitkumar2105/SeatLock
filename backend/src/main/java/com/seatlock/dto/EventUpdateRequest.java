package com.seatlock.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record EventUpdateRequest(
        @NotBlank String title,
        String description,
        @NotBlank String venueName,
        @NotNull @Future Instant eventDate
) {
}
