package com.seatlock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SeatBulkCreateRequest(
        @NotEmpty @Valid List<SeatDefinition> seats
) {
}
