package com.seatlock.dto;

import com.seatlock.entity.Role;
import com.seatlock.entity.User;

import java.util.UUID;

public record UserDto(UUID id, String name, String email, Role role) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
