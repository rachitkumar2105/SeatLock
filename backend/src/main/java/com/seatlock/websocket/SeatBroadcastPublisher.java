package com.seatlock.websocket;

import com.seatlock.entity.Seat;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

@Component
public class SeatBroadcastPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public SeatBroadcastPublisher(SimpMessagingTemplate messagingTemplate, ApplicationEventPublisher eventPublisher) {
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
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
        SeatStatusMessage message = new SeatStatusMessage(
                event.seat().getId(), event.seat().getStatus(), event.seat().getLockedBy(), event.seat().getLockExpiresAt()
        );
        messagingTemplate.convertAndSend("/topic/events/" + event.eventId() + "/seats", message);
    }

    public record SeatChangedEvent(UUID eventId, Seat seat) {
    }
}
