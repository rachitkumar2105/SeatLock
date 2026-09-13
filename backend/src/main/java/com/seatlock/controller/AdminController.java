package com.seatlock.controller;

import com.seatlock.dto.EventDto;
import com.seatlock.dto.PlatformMetricsDto;
import com.seatlock.dto.UpdateUserRoleRequest;
import com.seatlock.dto.UserDto;
import com.seatlock.service.AdminService;
import com.seatlock.service.EventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final EventService eventService;

    public AdminController(AdminService adminService, EventService eventService) {
        this.adminService = adminService;
        this.eventService = eventService;
    }

    @GetMapping("/users")
    public Page<UserDto> listUsers(@PageableDefault(size = 20) Pageable pageable) {
        return adminService.listUsers(pageable).map(UserDto::from);
    }

    @PatchMapping("/users/{id}/role")
    public UserDto updateUserRole(@PathVariable UUID id, @Valid @RequestBody UpdateUserRoleRequest request) {
        return UserDto.from(adminService.updateUserRole(id, request.role()));
    }

    @GetMapping("/events")
    public Page<EventDto> listEvents(@PageableDefault(size = 20) Pageable pageable) {
        return eventService.listAll(pageable).map(EventDto::from);
    }

    @PostMapping("/events/{id}/cancel")
    public EventDto cancelEvent(@PathVariable UUID id) {
        return EventDto.from(eventService.cancel(id));
    }

    @GetMapping("/metrics")
    public PlatformMetricsDto metrics() {
        return adminService.getMetrics();
    }
}
