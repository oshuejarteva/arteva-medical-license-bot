package com.arteva.medbot.util;

/**
 * Thread-safe fixed-window token bucket for rate limiting.
 * <p>
 * Tokens are refilled to full capacity at the start of each window.
 * Each {@link #tryConsume()} call decrements the available tokens
 * and returns {@code false} when exhausted.
 */
public class TokenBucket {

    private final int capacity;
    private final long windowMs;
    private int tokens;
    private long windowStart;

    public TokenBucket(int capacity, long windowMs) {
        this.capacity = capacity;
        this.windowMs = windowMs;
        this.tokens = capacity;
        this.windowStart = System.currentTimeMillis();
    }

    /**
     * Attempts to consume one token.
     *
     * @return true if a token was available, false if rate limit exceeded
     */
    public synchronized boolean tryConsume() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= windowMs) {
            tokens = capacity;
            windowStart = now;
        }
        if (tokens > 0) {
            tokens--;
            return true;
        }
        return false;
    }

    /**
     * Checks whether this bucket has been idle for longer than the given threshold.
     * Used for periodic cleanup of stale entries.
     *
     * @param now              current time in milliseconds
     * @param idleThresholdMs  idle threshold in milliseconds
     * @return true if bucket is expired
     */
    public synchronized boolean isExpired(long now, long idleThresholdMs) {
        return now - windowStart > idleThresholdMs;
    }
}
