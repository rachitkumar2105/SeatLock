package com.seatlock.controller;

import com.seatlock.dto.SeatBulkCreateRequest;
import com.seatlock.dto.SeatDto;
import com.seatlock.entity.Seat;
import com.seatlock.security.CurrentUser;
import com.seatlock.service.SeatLockService;
import com.seatlock.service.SeatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class SeatController {

    private final SeatService seatService;
    private final SeatLockService seatLockService;

    public SeatController(SeatService seatService, SeatLockService seatLockService) {
        this.seatService = seatService;
        this.seatLockService = seatLockService;
    }

    @GetMapping("/api/events/{eventId}/seats")
    public List<SeatDto> listSeats(@PathVariable UUID eventId) {
        return seatService.listByEvent(eventId).stream().map(SeatDto::from).toList();
    }

    @PreAuthorize("hasRole('ADMIN') or @eventGuard.isOwner(#eventId, authentication)")
    @PostMapping("/api/events/{eventId}/seats")
    public ResponseEntity<List<SeatDto>> createSeats(
            @PathVariable UUID eventId, @Valid @RequestBody SeatBulkCreateRequest request
    ) {
        List<Seat> seats = seatService.createSeats(eventId, request.seats());
        return ResponseEntity.status(201).body(seats.stream().map(SeatDto::from).toList());
    }

    @PostMapping("/api/seats/{seatId}/lock")
    public SeatDto lock(@PathVariable UUID seatId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        Seat seat = seatLockService.lock(seatId, currentUser.id());
        return SeatDto.from(seat);
    }

    @PostMapping("/api/seats/{seatId}/release")
    public ResponseEntity<Void> release(@PathVariable UUID seatId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        seatLockService.release(seatId, currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
