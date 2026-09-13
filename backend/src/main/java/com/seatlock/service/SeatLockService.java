package com.seatlock.service;

import com.seatlock.entity.Seat;
import com.seatlock.exception.ResourceNotFoundException;
import com.seatlock.exception.SeatConflictException;
import com.seatlock.repository.SeatRepository;
import com.seatlock.websocket.SeatBroadcastPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class SeatLockService {

    private final SeatRepository seatRepository;
    private final SeatBroadcastPublisher broadcastPublisher;
    private final long lockTtlMinutes;
    private final Counter lockSuccessCounter;
    private final Counter lockConflictCounter;

    public SeatLockService(
            SeatRepository seatRepository,
            SeatBroadcastPublisher broadcastPublisher,
            MeterRegistry meterRegistry,
            @Value("${app.seat-lock.ttl-minutes}") long lockTtlMinutes
    ) {
        this.seatRepository = seatRepository;
        this.broadcastPublisher = broadcastPublisher;
        this.lockTtlMinutes = lockTtlMinutes;
        // The metric the blueprint specifically calls out as worth having: how often a seat-lock
        // attempt loses the race. A healthy system still has a nonzero conflict rate on popular
        // events — that's the mechanism working as intended, not a problem — but a rate near 100%
        // would flag something upstream (e.g. the frontend retrying a doomed lock).
        this.lockSuccessCounter = Counter.builder("seatlock.lock.attempts")
                .description("Seat-lock attempts by outcome")
                .tag("outcome", "success")
                .register(meterRegistry);
        this.lockConflictCounter = Counter.builder("seatlock.lock.attempts")
                .description("Seat-lock attempts by outcome")
                .tag("outcome", "conflict")
                .register(meterRegistry);
    }

    @Transactional
    public Seat lock(UUID seatId, UUID userId) {
        Instant expiresAt = Instant.now().plus(lockTtlMinutes, ChronoUnit.MINUTES);
        int updated = seatRepository.tryLock(seatId, userId, expiresAt);

        if (updated == 0) {
            lockConflictCounter.increment();
            if (!seatRepository.existsById(seatId)) {
                throw new ResourceNotFoundException("Seat not found");
            }
            throw new SeatConflictException("Seat is not available");
        }

        lockSuccessCounter.increment();
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found"));
        broadcastPublisher.publishAfterCommit(seat.getEventId(), seat);
        return seat;
    }

    @Transactional
    public void release(UUID seatId, UUID userId) {
        int updated = seatRepository.releaseLock(seatId, userId);
        if (updated == 0) {
            throw new SeatConflictException("Seat is not locked by you");
        }

        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found"));
        broadcastPublisher.publishAfterCommit(seat.getEventId(), seat);
    }
}
