package com.arteva.medbot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {

    @Test
    void tryConsume_shouldAllowUpToCapacity() {
        TokenBucket bucket = new TokenBucket(3, 60_000L);

        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume()); // exhausted
    }

    @Test
    void tryConsume_shouldRefillAfterWindowExpires() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(2, 50L); // 50ms window

        assertTrue(bucket.tryConsume());
        assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume());

        Thread.sleep(60);

        assertTrue(bucket.tryConsume()); // refilled
    }

    @Test
    void isExpired_shouldReturnTrueWhenIdle() {
        TokenBucket bucket = new TokenBucket(5, 100L);

        long now = System.currentTimeMillis();
        assertFalse(bucket.isExpired(now, 1000L));
        assertTrue(bucket.isExpired(now + 2000L, 1000L));
    }
}
