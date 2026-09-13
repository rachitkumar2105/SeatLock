package com.seatlock.scheduler;

import com.seatlock.entity.Seat;
import com.seatlock.entity.SeatStatus;
import com.seatlock.repository.SeatRepository;
import com.seatlock.websocket.SeatBroadcastPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class LockExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(LockExpiryJob.class);

    private final SeatRepository seatRepository;
    private final SeatBroadcastPublisher broadcastPublisher;

    public LockExpiryJob(SeatRepository seatRepository, SeatBroadcastPublisher broadcastPublisher) {
        this.seatRepository = seatRepository;
        this.broadcastPublisher = broadcastPublisher;
    }

    @Scheduled(fixedDelayString = "${app.seat-lock.expiry-check-interval-ms}")
    @Transactional
    public void revertExpiredLocks() {
        Instant now = Instant.now();
        List<Seat> candidates = seatRepository.findByStatusAndLockExpiresAtBefore(SeatStatus.LOCKED, now);

        int reverted = 0;
        for (Seat candidate : candidates) {
            int updated = seatRepository.revertIfStillExpired(candidate.getId(), now);
            if (updated == 1) {
                reverted++;
                Seat seat = seatRepository.findById(candidate.getId()).orElseThrow();
                broadcastPublisher.publishAfterCommit(seat.getEventId(), seat);
            }
        }

        if (reverted > 0) {
            log.info("Reverted {} expired seat lock(s) to AVAILABLE", reverted);
        }
    }
}
