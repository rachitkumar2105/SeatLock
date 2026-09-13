package com.seatlock.repository;

import com.seatlock.entity.Seat;
import com.seatlock.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByEventIdOrderBySectionAscRowAscNumberAsc(UUID eventId);

    List<Seat> findByStatusAndLockExpiresAtBefore(SeatStatus status, Instant instant);

    long countByEventId(UUID eventId);

    long countByEventIdAndStatus(UUID eventId, SeatStatus status);

    /**
     * The core correctness guarantee: a single atomic conditional UPDATE. Postgres serializes
     * concurrent writes to the same row, so of N concurrent calls racing for the same seat, exactly
     * one sees status = AVAILABLE and updates 1 row; every other call updates 0 rows.
     */
    @Modifying
    @Query("""
            update Seat s
            set s.status = com.seatlock.entity.SeatStatus.LOCKED,
                s.lockedBy = :userId,
                s.lockExpiresAt = :expiresAt,
                s.version = s.version + 1
            where s.id = :seatId
              and s.status = com.seatlock.entity.SeatStatus.AVAILABLE
            """)
    int tryLock(@Param("seatId") UUID seatId, @Param("userId") UUID userId, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query("""
            update Seat s
            set s.status = com.seatlock.entity.SeatStatus.AVAILABLE,
                s.lockedBy = null,
                s.lockExpiresAt = null,
                s.version = s.version + 1
            where s.id = :seatId
              and s.lockedBy = :userId
              and s.status = com.seatlock.entity.SeatStatus.LOCKED
            """)
    int releaseLock(@Param("seatId") UUID seatId, @Param("userId") UUID userId);

    @Modifying
    @Query("""
            update Seat s
            set s.status = com.seatlock.entity.SeatStatus.AVAILABLE,
                s.lockedBy = null,
                s.lockExpiresAt = null,
                s.version = s.version + 1
            where s.id = :seatId
              and s.status = com.seatlock.entity.SeatStatus.LOCKED
              and s.lockExpiresAt < :now
            """)
    int revertIfStillExpired(@Param("seatId") UUID seatId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update Seat s
            set s.status = com.seatlock.entity.SeatStatus.BOOKED,
                s.lockedBy = null,
                s.lockExpiresAt = null,
                s.version = s.version + 1
            where s.id = :seatId
              and s.lockedBy = :userId
              and s.status = com.seatlock.entity.SeatStatus.LOCKED
            """)
    int confirmBooked(@Param("seatId") UUID seatId, @Param("userId") UUID userId);

    @Modifying
    @Query("""
            update Seat s
            set s.status = com.seatlock.entity.SeatStatus.AVAILABLE,
                s.lockedBy = null,
                s.lockExpiresAt = null,
                s.version = s.version + 1
            where s.id = :seatId
              and s.status = com.seatlock.entity.SeatStatus.BOOKED
            """)
    int releaseBooked(@Param("seatId") UUID seatId);
}
