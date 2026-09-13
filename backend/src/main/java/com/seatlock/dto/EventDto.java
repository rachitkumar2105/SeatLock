package com.seatlock.dto;

import com.seatlock.entity.Event;
import com.seatlock.entity.EventStatus;

import java.time.Instant;
import java.util.UUID;

public record EventDto(
        UUID id,
        UUID organizerId,
        String title,
        String description,
        String venueName,
        Instant eventDate,
        EventStatus status
) {
    public static EventDto from(Event event) {
        return new EventDto(
                event.getId(),
                event.getOrganizerId(),
                event.getTitle(),
                event.getDescription(),
                event.getVenueName(),
                event.getEventDate(),
                event.getStatus()
        );
    }
}
