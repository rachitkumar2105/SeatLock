package com.seatlock.service;

import com.seatlock.entity.Event;
import com.seatlock.entity.EventStatus;
import com.seatlock.exception.ApiException;
import com.seatlock.repository.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Page<Event> listPublished(Pageable pageable) {
        return eventRepository.findByStatus(EventStatus.PUBLISHED, pageable);
    }

    public Event getById(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event not found"));
    }

    @Transactional
    public Event create(UUID organizerId, String title, String description, String venueName, Instant eventDate) {
        Event event = new Event();
        event.setOrganizerId(organizerId);
        event.setTitle(title);
        event.setDescription(description);
        event.setVenueName(venueName);
        event.setEventDate(eventDate);
        event.setStatus(EventStatus.DRAFT);
        return eventRepository.save(event);
    }

    @Transactional
    public Event update(UUID id, String title, String description, String venueName, Instant eventDate) {
        Event event = getById(id);
        event.setTitle(title);
        event.setDescription(description);
        event.setVenueName(venueName);
        event.setEventDate(eventDate);
        return eventRepository.save(event);
    }

    @Transactional
    public Event publish(UUID id) {
        Event event = getById(id);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw ApiException.conflict("Only a DRAFT event can be published");
        }
        event.setStatus(EventStatus.PUBLISHED);
        return eventRepository.save(event);
    }
}
