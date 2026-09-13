package com.seatlock.service;

import com.seatlock.dto.SeatDefinition;
import com.seatlock.entity.Seat;
import com.seatlock.entity.SeatStatus;
import com.seatlock.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SeatService {

    private final SeatRepository seatRepository;

    public SeatService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    public List<Seat> listByEvent(UUID eventId) {
        return seatRepository.findByEventIdOrderBySectionAscRowAscNumberAsc(eventId);
    }

    @Transactional
    public List<Seat> createSeats(UUID eventId, List<SeatDefinition> definitions) {
        List<Seat> seats = definitions.stream().map(def -> {
            Seat seat = new Seat();
            seat.setEventId(eventId);
            seat.setSection(def.section());
            seat.setRow(def.row());
            seat.setNumber(def.number());
            seat.setPrice(def.price());
            seat.setStatus(SeatStatus.AVAILABLE);
            return seat;
        }).toList();
        return seatRepository.saveAll(seats);
    }
}
