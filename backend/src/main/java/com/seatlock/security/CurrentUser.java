package com.seatlock.security;

import java.util.UUID;

public record CurrentUser(UUID id, String email, String role) {
}
