package com.seatlock.dto;

import java.math.BigDecimal;

public record PlatformMetricsDto(
        long totalUsers,
        long totalEvents,
        long publishedEvents,
        long totalBookings,
        BigDecimal totalRevenue
) {
}
