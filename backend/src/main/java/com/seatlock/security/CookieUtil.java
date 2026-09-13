package com.seatlock.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieUtil {

    public static final String REFRESH_COOKIE_NAME = "refresh_token";
    public static final String CSRF_COOKIE_NAME = "csrf_token";

    private final long refreshTokenTtlDays;

    public CookieUtil(@Value("${app.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public ResponseCookie buildRefreshCookie(String rawToken) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ofDays(refreshTokenTtlDays))
                .build();
    }

    // Path="/" (unlike the refresh cookie) so frontend JS can read it via document.cookie from any
    // page — it's meant to be readable; that's the whole point of the double-submit pattern.
    public ResponseCookie buildCsrfCookie(String csrfToken) {
        return ResponseCookie.from(CSRF_COOKIE_NAME, csrfToken)
                .httpOnly(false)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(refreshTokenTtlDays))
                .build();
    }

    public ResponseCookie buildExpiredRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
    }

    public ResponseCookie buildExpiredCsrfCookie() {
        return ResponseCookie.from(CSRF_COOKIE_NAME, "")
                .httpOnly(false)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
