package com.seatlock.service;

import com.seatlock.config.CacheNames;
import com.seatlock.dto.SeatDefinition;
import com.seatlock.entity.Seat;
import com.seatlock.entity.SeatStatus;
import com.seatlock.repository.SeatRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    /**
     * The hottest read in the system (every viewer of an event's seat page hits this). Cached in
     * Redis with a short TTL as a backstop; the real invalidation path is the explicit evict below,
     * fired from the single funnel point every seat-status change already passes through
     * (SeatBroadcastPublisher). If Redis is down, @Cacheable just misses every time — see RedisConfig.
     */
    @Cacheable(cacheNames = CacheNames.SEAT_MAPS, key = "#eventId")
    public List<Seat> listByEvent(UUID eventId) {
        return seatRepository.findByEventIdOrderBySectionAscRowAscNumberAsc(eventId);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.SEAT_MAPS, key = "#eventId")
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
