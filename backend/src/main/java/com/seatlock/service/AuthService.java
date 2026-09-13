package com.seatlock.service;

import com.seatlock.entity.RefreshToken;
import com.seatlock.entity.Role;
import com.seatlock.entity.User;
import com.seatlock.exception.AuthenticationFailedException;
import com.seatlock.exception.ConflictException;
import com.seatlock.exception.ResourceNotFoundException;
import com.seatlock.repository.RefreshTokenRepository;
import com.seatlock.repository.UserRepository;
import com.seatlock.security.JwtService;
import com.seatlock.security.TokenHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenTtlDays;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    @Transactional
    public User register(String name, String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    public IssuedTokens login(String email, String rawPassword, String userAgent, String ipAddress) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid credentials"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid credentials");
        }

        return issueTokens(user, userAgent, ipAddress);
    }

    @Transactional
    public IssuedTokens refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthenticationFailedException("Missing refresh token");
        }

        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token"));

        if (stored.isRevoked()) {
            // Reuse of a revoked token is a signal of theft: kill the entire session family.
            refreshTokenRepository.revokeAllForUser(stored.getUserId());
            throw new AuthenticationFailedException("Refresh token has been revoked");
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthenticationFailedException("Refresh token has expired");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new AuthenticationFailedException("User no longer exists"));

        return issueTokens(user, userAgent, ipAddress);
    }

    public List<RefreshToken> listSessions(UUID userId) {
        return refreshTokenRepository.findActiveSessions(userId, Instant.now());
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        int updated = refreshTokenRepository.revokeOne(sessionId, userId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Session not found");
        }
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private IssuedTokens issueTokens(User user, String userAgent, String ipAddress) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());

        String rawRefreshToken = TokenHasher.generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(TokenHasher.sha256Hex(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plus(refreshTokenTtlDays, ChronoUnit.DAYS));
        refreshToken.setRevoked(false);
        refreshToken.setUserAgent(trimTo(userAgent, 500));
        refreshToken.setIpAddress(trimTo(ipAddress, 64));
        refreshToken.setLastUsedAt(Instant.now());
        refreshTokenRepository.save(refreshToken);

        return new IssuedTokens(user, accessToken, jwtService.getAccessTokenTtlMinutes() * 60, rawRefreshToken);
    }

    private static String trimTo(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record IssuedTokens(User user, String accessToken, long expiresInSeconds, String rawRefreshToken) {
    }
}
