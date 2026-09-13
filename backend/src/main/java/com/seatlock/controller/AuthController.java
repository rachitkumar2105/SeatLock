package com.seatlock.controller;

import com.seatlock.dto.AuthResponse;
import com.seatlock.dto.LoginRequest;
import com.seatlock.dto.RegisterRequest;
import com.seatlock.dto.UserDto;
import com.seatlock.entity.User;
import com.seatlock.exception.ApiException;
import com.seatlock.security.CookieUtil;
import com.seatlock.security.TokenHasher;
import com.seatlock.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.IssuedTokens tokens = authService.login(request.email(), request.password());
        return withAuthCookies(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = CookieUtil.REFRESH_COOKIE_NAME, required = false) String refreshCookie,
            @CookieValue(name = CookieUtil.CSRF_COOKIE_NAME, required = false) String csrfCookie,
            @RequestHeader(name = CSRF_HEADER_NAME, required = false) String csrfHeader
    ) {
        verifyCsrf(csrfCookie, csrfHeader);
        AuthService.IssuedTokens tokens = authService.refresh(refreshCookie);
        return withAuthCookies(tokens);
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
