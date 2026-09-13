package com.seatlock.service;

import com.seatlock.entity.Seat;
import com.seatlock.exception.ApiException;
import com.seatlock.repository.SeatRepository;
import com.seatlock.websocket.SeatBroadcastPublisher;
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

    public SeatLockService(
            SeatRepository seatRepository,
            SeatBroadcastPublisher broadcastPublisher,
            @Value("${app.seat-lock.ttl-minutes}") long lockTtlMinutes
    ) {
        this.seatRepository = seatRepository;
        this.broadcastPublisher = broadcastPublisher;
        this.lockTtlMinutes = lockTtlMinutes;
    }

    @Transactional
    public Seat lock(UUID seatId, UUID userId) {
        Instant expiresAt = Instant.now().plus(lockTtlMinutes, ChronoUnit.MINUTES);
        int updated = seatRepository.tryLock(seatId, userId, expiresAt);

        if (updated == 0) {
            if (!seatRepository.existsById(seatId)) {
                throw ApiException.notFound("Seat not found");
            }
            throw ApiException.conflict("Seat is not available");
        }

        Seat seat = seatRepository.findById(seatId).orElseThrow(() -> ApiException.notFound("Seat not found"));
        broadcastPublisher.publishAfterCommit(seat.getEventId(), seat);
        return seat;
    }

    @Transactional
    public void release(UUID seatId, UUID userId) {
        int updated = seatRepository.releaseLock(seatId, userId);
        if (updated == 0) {
            throw ApiException.conflict("Seat is not locked by you");
        }

        Seat seat = seatRepository.findById(seatId).orElseThrow(() -> ApiException.notFound("Seat not found"));
        broadcastPublisher.publishAfterCommit(seat.getEventId(), seat);
    }
}
