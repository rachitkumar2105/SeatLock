package com.seatlock.service;

import com.seatlock.entity.*;
import com.seatlock.exception.BadRequestException;
import com.seatlock.exception.ResourceNotFoundException;
import com.seatlock.exception.SeatConflictException;
import com.seatlock.exception.UnauthorizedActionException;
import com.seatlock.exception.ConflictException;
import com.seatlock.repository.BookingRepository;
import com.seatlock.repository.BookingSeatRepository;
import com.seatlock.repository.PaymentRepository;
import com.seatlock.repository.SeatRepository;
import com.seatlock.websocket.SeatBroadcastPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatRepository seatRepository;
    private final PaymentRepository paymentRepository;
    private final SeatBroadcastPublisher broadcastPublisher;

    public BookingService(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            SeatRepository seatRepository,
            PaymentRepository paymentRepository,
            SeatBroadcastPublisher broadcastPublisher
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.seatRepository = seatRepository;
        this.paymentRepository = paymentRepository;
        this.broadcastPublisher = broadcastPublisher;
    }

    // REPEATABLE_READ (not the READ COMMITTED default used elsewhere): this transaction reads each
    // seat's status, then re-verifies and writes it via confirmBooked's conditional UPDATE. Postgres's
    // READ COMMITTED would let a concurrent transaction change a row between our read and our write;
    // REPEATABLE READ pins the snapshot for the whole transaction, so a conflicting concurrent booking
    // fails with a serialization error instead of silently interleaving. See docs/adr/0003.
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Booking createBooking(UUID userId, UUID eventId, List<UUID> seatIds, String idempotencyKey) {
        var existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new ResourceNotFoundException("One or more seats do not exist");
        }
        boolean allBelongToEvent = seats.stream().allMatch(s -> s.getEventId().equals(eventId));
        if (!allBelongToEvent) {
            throw new BadRequestException("All seats must belong to the requested event");
        }

        for (UUID seatId : seatIds) {
            int updated = seatRepository.confirmBooked(seatId, userId);
            if (updated == 0) {
                throw new SeatConflictException("Seat " + seatId + " is no longer locked by you");
            }
            Seat bookedSeat = seatRepository.findById(seatId).orElseThrow();
            broadcastPublisher.publishAfterCommit(eventId, bookedSeat);
        }

        BigDecimal totalAmount = seats.stream()
                .map(Seat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setEventId(eventId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(totalAmount);
        booking.setIdempotencyKey(idempotencyKey);
        booking = bookingRepository.save(booking);

        for (UUID seatId : seatIds) {
            bookingSeatRepository.save(new BookingSeat(booking.getId(), seatId));
        }

        Payment payment = new Payment();
        payment.setBookingId(booking.getId());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmount(totalAmount);
        payment.setProviderRef("mock-" + UUID.randomUUID());
        paymentRepository.save(payment);

        return booking;
    }

    public List<Booking> listForUser(UUID userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<UUID> seatIdsFor(UUID bookingId) {
        return bookingSeatRepository.findByBookingId(bookingId).stream()
                .map(BookingSeat::getSeatId)
                .toList();
    }

    public Booking getById(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    @Transactional
    public Booking cancel(UUID bookingId, UUID userId) {
        Booking booking = getById(bookingId);
        if (!booking.getUserId().equals(userId)) {
            throw new UnauthorizedActionException("You can only cancel your own bookings");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Only a CONFIRMED booking can be cancelled");
        }

        for (UUID seatId : seatIdsFor(bookingId)) {
            int updated = seatRepository.releaseBooked(seatId);
            if (updated == 1) {
                Seat seat = seatRepository.findById(seatId).orElseThrow();
                broadcastPublisher.publishAfterCommit(booking.getEventId(), seat);
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return bookingRepository.save(booking);
    }
}
