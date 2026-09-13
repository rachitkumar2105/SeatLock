package com.seatlock.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as rate-limited. {@code bucket} selects which
 * {@link RateLimitProperties} bucket (limit + window) applies.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimited {
    RateLimitBucket bucket();
}
