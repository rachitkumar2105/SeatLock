package com.seatlock.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private Bucket login = new Bucket(10, 60);
    private Bucket seatLock = new Bucket(30, 60);
    private Bucket booking = new Bucket(10, 60);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Bucket getLogin() {
        return login;
    }

    public void setLogin(Bucket login) {
        this.login = login;
    }

    public Bucket getSeatLock() {
        return seatLock;
    }

    public void setSeatLock(Bucket seatLock) {
        this.seatLock = seatLock;
    }

    public Bucket getBooking() {
        return booking;
    }

    public void setBooking(Bucket booking) {
        this.booking = booking;
    }

    public static class Bucket {
        private int limit;
        private int windowSeconds;

        public Bucket() {
        }

        public Bucket(int limit, int windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
