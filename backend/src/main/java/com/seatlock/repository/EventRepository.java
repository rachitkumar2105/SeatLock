package com.seatlock.repository;

import com.seatlock.entity.Event;
import com.seatlock.entity.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    Page<Event> findByOrganizerId(UUID organizerId, Pageable pageable);

    long countByStatus(EventStatus status);
}
