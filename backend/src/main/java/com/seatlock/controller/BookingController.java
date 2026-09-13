package com.seatlock.controller;

import com.seatlock.dto.BookingCreateRequest;
import com.seatlock.dto.BookingDto;
import com.seatlock.entity.Booking;
import com.seatlock.exception.ApiException;
import com.seatlock.ratelimit.RateLimitBucket;
import com.seatlock.ratelimit.RateLimited;
import com.seatlock.security.CurrentUser;
import com.seatlock.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @RateLimited(bucket = RateLimitBucket.BOOKING)
    @PostMapping
    public ResponseEntity<BookingDto> create(
            @Valid @RequestBody BookingCreateRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("Idempotency-Key header is required");
        }
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        Booking booking = bookingService.createBooking(
                currentUser.id(), request.eventId(), request.seatIds(), idempotencyKey
        );
        List<UUID> seatIds = bookingService.seatIdsFor(booking.getId());
        return ResponseEntity.status(201).body(BookingDto.from(booking, seatIds));
    }

    @GetMapping("/me")
    public List<BookingDto> myBookings(Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return bookingService.listForUser(currentUser.id()).stream()
                .map(booking -> BookingDto.from(booking, bookingService.seatIdsFor(booking.getId())))
                .toList();
    }

    @PostMapping("/{id}/cancel")
    public BookingDto cancel(@PathVariable UUID id, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        Booking booking = bookingService.cancel(id, currentUser.id());
        return BookingDto.from(booking, bookingService.seatIdsFor(booking.getId()));
    }
}
