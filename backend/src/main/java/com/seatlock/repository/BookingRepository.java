package com.seatlock.repository;

import com.seatlock.entity.Booking;
import com.seatlock.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    List<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
            select coalesce(sum(b.totalAmount), 0) from Booking b
            where b.eventId = :eventId and b.status = :status
            """)
    BigDecimal sumTotalAmountByEventIdAndStatus(@Param("eventId") UUID eventId, @Param("status") BookingStatus status);

    @Query("select coalesce(sum(b.totalAmount), 0) from Booking b where b.status = :status")
    BigDecimal sumTotalAmountByStatus(@Param("status") BookingStatus status);
}
