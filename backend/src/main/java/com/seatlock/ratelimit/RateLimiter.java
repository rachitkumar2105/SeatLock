package com.seatlock.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * A fixed-window counter (INCR + EXPIRE on first hit) keyed per caller. Simple rather than
 * perfectly smooth (a burst can straddle a window boundary), which is the right trade for a
 * login/seat-lock/booking guard — exactness matters less than "can't be trivially hammered."
 * <p>
 * Fails open: if Redis is unreachable, {@link #tryAcquire} returns {@code true} and logs a
 * warning rather than blocking every request. Rate limiting is a defense-in-depth layer, not a
 * correctness guarantee — the seat-lock atomicity in {@code SeatRepository} is what actually
 * prevents double-booking, so losing the rate limiter under a Redis outage degrades safety
 * margin, not correctness.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        try {
            String redisKey = "ratelimit:" + key;
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }
            return count <= limit;
        } catch (RuntimeException e) {
            log.warn("Rate limiter check failed for key '{}' — allowing the request through: {}", key, e.getMessage());
            return true;
        }
    }
}
