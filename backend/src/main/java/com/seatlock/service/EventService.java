package com.seatlock.service;

import com.seatlock.dto.EventStatsDto;
import com.seatlock.entity.BookingStatus;
import com.seatlock.entity.Event;
import com.seatlock.entity.EventStatus;
import com.seatlock.entity.SeatStatus;
import com.seatlock.exception.ConflictException;
import com.seatlock.exception.ResourceNotFoundException;
import com.seatlock.repository.BookingRepository;
import com.seatlock.repository.EventRepository;
import com.seatlock.repository.SeatRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;

    public EventService(EventRepository eventRepository, SeatRepository seatRepository, BookingRepository bookingRepository) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
    }

    public Page<Event> listPublished(Pageable pageable) {
        return eventRepository.findByStatus(EventStatus.PUBLISHED, pageable);
    }

    public Page<Event> listByOrganizer(UUID organizerId, Pageable pageable) {
        return eventRepository.findByOrganizerId(organizerId, pageable);
    }

    public Page<Event> listAll(Pageable pageable) {
        return eventRepository.findAll(pageable);
    }

    public EventStatsDto getStats(UUID eventId) {
        getById(eventId);
        long total = seatRepository.countByEventId(eventId);
        long available = seatRepository.countByEventIdAndStatus(eventId, SeatStatus.AVAILABLE);
        long locked = seatRepository.countByEventIdAndStatus(eventId, SeatStatus.LOCKED);
        long booked = seatRepository.countByEventIdAndStatus(eventId, SeatStatus.BOOKED);
        var revenue = bookingRepository.sumTotalAmountByEventIdAndStatus(eventId, BookingStatus.CONFIRMED);
        return new EventStatsDto(eventId, total, available, locked, booked, revenue);
    }

    public Event getById(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
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
            throw new ConflictException("Only a DRAFT event can be published");
        }
        event.setStatus(EventStatus.PUBLISHED);
        return eventRepository.save(event);
    }

    @Transactional
    public Event cancel(UUID id) {
        Event event = getById(id);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("Event is already cancelled");
        }
        event.setStatus(EventStatus.CANCELLED);
        return eventRepository.save(event);
    }
}
