package com.seatlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SeatDefinition(
        @NotBlank String section,
        @NotBlank String row,
        @Positive int number,
        @NotNull @Positive BigDecimal price
) {
}
