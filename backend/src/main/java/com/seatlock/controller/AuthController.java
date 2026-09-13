package com.seatlock.controller;

import com.seatlock.dto.AuthResponse;
import com.seatlock.dto.LoginRequest;
import com.seatlock.dto.RegisterRequest;
import com.seatlock.dto.SessionDto;
import com.seatlock.dto.UserDto;
import com.seatlock.entity.RefreshToken;
import com.seatlock.entity.User;
import com.seatlock.exception.ApiException;
import com.seatlock.security.CookieUtil;
import com.seatlock.security.CurrentUser;
import com.seatlock.security.TokenHasher;
import com.seatlock.ratelimit.RateLimitBucket;
import com.seatlock.ratelimit.RateLimited;
import com.seatlock.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    public AuthController(AuthService authService, CookieUtil cookieUtil) {
        this.authService = authService;
        this.cookieUtil = cookieUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.name(), request.email(), request.password());
        return ResponseEntity.status(201).body(UserDto.from(user));
    }

    @RateLimited(bucket = RateLimitBucket.LOGIN)
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response,
            HttpServletRequest httpRequest
    ) {
        AuthService.IssuedTokens tokens = authService.login(
                request.email(), request.password(), userAgentOf(httpRequest), ipAddressOf(httpRequest)
        );
        return withAuthCookies(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = CookieUtil.REFRESH_COOKIE_NAME, required = false) String refreshCookie,
            @CookieValue(name = CookieUtil.CSRF_COOKIE_NAME, required = false) String csrfCookie,
            @RequestHeader(name = CSRF_HEADER_NAME, required = false) String csrfHeader,
            HttpServletRequest httpRequest
    ) {
        verifyCsrf(csrfCookie, csrfHeader);
        AuthService.IssuedTokens tokens = authService.refresh(
                refreshCookie, userAgentOf(httpRequest), ipAddressOf(httpRequest)
        );
        return withAuthCookies(tokens);
    }

    @GetMapping("/sessions")
    public List<SessionDto> listSessions(Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return authService.listSessions(currentUser.id()).stream().map(SessionDto::from).toList();
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID id, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        authService.revokeSession(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    private static String userAgentOf(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    // Deliberately request.getRemoteAddr() only, not X-Forwarded-For: that header is
    // client-suppliable and would let a caller spoof the IP recorded against their own session
    // unless a trusted reverse proxy is guaranteed to overwrite it before this app sees it, which
    // isn't guaranteed for a project run behind an arbitrary deployment.
    private static String ipAddressOf(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = CookieUtil.REFRESH_COOKIE_NAME, required = false) String refreshCookie,
            @CookieValue(name = CookieUtil.CSRF_COOKIE_NAME, required = false) String csrfCookie,
            @RequestHeader(name = CSRF_HEADER_NAME, required = false) String csrfHeader
    ) {
        verifyCsrf(csrfCookie, csrfHeader);
        authService.logout(refreshCookie);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.buildExpiredRefreshCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.buildExpiredCsrfCookie().toString())
                .build();
    }

    private void verifyCsrf(String csrfCookie, String csrfHeader) {
        if (csrfCookie == null || csrfHeader == null || !csrfCookie.equals(csrfHeader)) {
            throw ApiException.forbidden("CSRF token missing or invalid");
        }
    }

    private ResponseEntity<AuthResponse> withAuthCookies(AuthService.IssuedTokens tokens) {
        String csrfToken = TokenHasher.generateOpaqueToken();
        AuthResponse body = new AuthResponse(tokens.accessToken(), tokens.expiresInSeconds(), UserDto.from(tokens.user()));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.buildRefreshCookie(tokens.rawRefreshToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.buildCsrfCookie(csrfToken).toString())
                .body(body);
    }
}
