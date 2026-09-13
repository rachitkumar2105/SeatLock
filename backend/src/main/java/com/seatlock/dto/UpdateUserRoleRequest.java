package com.seatlock.dto;

import com.seatlock.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull Role role) {
}
