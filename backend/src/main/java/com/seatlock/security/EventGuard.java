package com.seatlock.security;

import com.seatlock.repository.EventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("eventGuard")
public class EventGuard {

    private final EventRepository eventRepository;

    public EventGuard(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public boolean isOwner(UUID eventId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return false;
        }
        if ("ADMIN".equals(currentUser.role())) {
            return true;
        }
        return eventRepository.findById(eventId)
                .map(event -> event.getOrganizerId().equals(currentUser.id()))
                .orElse(false);
    }
}
