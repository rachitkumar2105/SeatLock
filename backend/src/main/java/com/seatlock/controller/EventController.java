package com.seatlock.controller;

import com.seatlock.dto.EventCreateRequest;
import com.seatlock.dto.EventDto;
import com.seatlock.dto.EventUpdateRequest;
import com.seatlock.entity.Event;
import com.seatlock.security.CurrentUser;
import com.seatlock.service.EventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public Page<EventDto> list(@PageableDefault(size = 20) Pageable pageable) {
        return eventService.listPublished(pageable).map(EventDto::from);
    }

    @GetMapping("/{id}")
    public EventDto get(@PathVariable UUID id) {
        return EventDto.from(eventService.getById(id));
    }

    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @PostMapping
    public ResponseEntity<EventDto> create(@Valid @RequestBody EventCreateRequest request, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        Event event = eventService.create(
                currentUser.id(), request.title(), request.description(), request.venueName(), request.eventDate()
        );
        return ResponseEntity.status(201).body(EventDto.from(event));
    }

    @PreAuthorize("hasRole('ADMIN') or @eventGuard.isOwner(#id, authentication)")
    @PutMapping("/{id}")
    public EventDto update(@PathVariable UUID id, @Valid @RequestBody EventUpdateRequest request) {
        Event event = eventService.update(
                id, request.title(), request.description(), request.venueName(), request.eventDate()
        );
        return EventDto.from(event);
    }

    @PreAuthorize("hasRole('ADMIN') or @eventGuard.isOwner(#id, authentication)")
    @PostMapping("/{id}/publish")
    public EventDto publish(@PathVariable UUID id) {
        return EventDto.from(eventService.publish(id));
    }
}
