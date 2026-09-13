package com.seatlock.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenTtlMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
    }

    public long getAccessTokenTtlMinutes() {
        return accessTokenTtlMinutes;
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES)))
                // Pin HS256 explicitly rather than letting jjwt infer an algorithm from key length.
                // parseClaims below never reads the token's own `alg` header to decide how to verify
                // it (verifyWith(key) is unconditional), which is what actually closes the classic
                // "alg: none" / algorithm-confusion forgery class — pinning here just keeps the
                // signer and verifier in obvious, explicit agreement rather than implicit.
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<Claims> parseClaims(String token) {
        try {
            // verifyWith(signingKey) forces signature verification with our own known key — the
            // token's self-declared `alg` header is never trusted to pick the verification method,
            // so a forged "alg: none" or algorithm-substituted token fails here regardless.
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
