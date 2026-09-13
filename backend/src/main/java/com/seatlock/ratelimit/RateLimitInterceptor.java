package com.seatlock.ratelimit;

import com.seatlock.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public RateLimitInterceptor(RateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!properties.isEnabled() || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimited annotation = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (annotation == null) {
            return true;
        }

        RateLimitProperties.Bucket bucket = resolveBucket(annotation.bucket());
        String key = annotation.bucket().name() + ":" + resolveIdentity(request);

        if (rateLimiter.tryAcquire(key, bucket.getLimit(), bucket.getWindowSeconds())) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(bucket.getWindowSeconds()));
        response.setContentType("application/json");
        response.getWriter().write("""
                {"timestamp":"%s","status":429,"error":"Too Many Requests","message":"Rate limit exceeded, please try again shortly.","details":[]}"""
                .formatted(Instant.now()));
        return false;
    }

    private RateLimitProperties.Bucket resolveBucket(RateLimitBucket bucket) {
        return switch (bucket) {
            case LOGIN -> properties.getLogin();
            case SEAT_LOCK -> properties.getSeatLock();
            case BOOKING -> properties.getBooking();
        };
    }

    private String resolveIdentity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser) {
            return "user:" + currentUser.id();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
