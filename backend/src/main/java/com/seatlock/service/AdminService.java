package com.seatlock.service;

import com.seatlock.dto.PlatformMetricsDto;
import com.seatlock.entity.BookingStatus;
import com.seatlock.entity.EventStatus;
import com.seatlock.entity.Role;
import com.seatlock.entity.User;
import com.seatlock.exception.ResourceNotFoundException;
import com.seatlock.repository.BookingRepository;
import com.seatlock.repository.EventRepository;
import com.seatlock.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;

    public AdminService(UserRepository userRepository, EventRepository eventRepository, BookingRepository bookingRepository) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
    }

    public Page<User> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional
    public User updateUserRole(UUID userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setRole(role);
        return userRepository.save(user);
    }

    public PlatformMetricsDto getMetrics() {
        long totalUsers = userRepository.count();
        long totalEvents = eventRepository.count();
        long publishedEvents = eventRepository.countByStatus(EventStatus.PUBLISHED);
        long totalBookings = bookingRepository.count();
        var totalRevenue = bookingRepository.sumTotalAmountByStatus(BookingStatus.CONFIRMED);
        return new PlatformMetricsDto(totalUsers, totalEvents, publishedEvents, totalBookings, totalRevenue);
    }
}
