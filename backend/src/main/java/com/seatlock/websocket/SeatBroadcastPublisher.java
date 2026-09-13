package com.seatlock.websocket;

import com.seatlock.config.CacheNames;
import com.seatlock.entity.Seat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

@Component
public class SeatBroadcastPublisher {

    private static final Logger log = LoggerFactory.getLogger(SeatBroadcastPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheManager cacheManager;

    public SeatBroadcastPublisher(
            SimpMessagingTemplate messagingTemplate, ApplicationEventPublisher eventPublisher, CacheManager cacheManager
    ) {
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
        this.cacheManager = cacheManager;
    }

    /**
     * Queues a broadcast to fire only after the enclosing DB transaction commits, so viewers never
     * see a seat status that the database itself doesn't have yet (e.g. on a rolled-back booking).
     */
    public void publishAfterCommit(UUID eventId, Seat seat) {
        eventPublisher.publishEvent(new SeatChangedEvent(eventId, seat));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatChanged(SeatChangedEvent event) {
        evictSeatMapCache(event.eventId());

        SeatStatusMessage message = new SeatStatusMessage(
                event.seat().getId(), event.seat().getStatus(), event.seat().getLockedBy(), event.seat().getLockExpiresAt()
        );
        messagingTemplate.convertAndSend("/topic/events/" + event.eventId() + "/seats", message);
    }

    private void evictSeatMapCache(UUID eventId) {
        try {
            Cache cache = cacheManager.getCache(CacheNames.SEAT_MAPS);
            if (cache != null) {
                cache.evict(eventId);
            }
        } catch (RuntimeException e) {
            // Same "degrade, don't break" contract as RedisConfig's CacheErrorHandler — this call
            // isn't behind the @Cacheable/@CacheEvict AOP proxy, so we swallow it ourselves. A
            // failed evict just means the seat-map cache TTL (see RedisConfig) is the backstop.
            log.warn("Redis cache evict failed for event {}: {}", eventId, e.getMessage());
        }
    }

    public record SeatChangedEvent(UUID eventId, Seat seat) {
    }
}
