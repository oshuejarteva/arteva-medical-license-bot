package com.arteva.medbot.config;

import com.arteva.medbot.util.TokenBucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-IP rate limiting filter for HTTP endpoints.
 * Uses a fixed-window token bucket per client IP address.
 * <p>
 * Old entries are cleaned up every 5 minutes to prevent memory leaks.
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${rate-limit.requests-per-minute:30}") int requestsPerMinute) {
        this.maxRequests = requestsPerMinute;
        this.windowMs = 60_000L;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        String path = httpReq.getRequestURI();

        // Only rate-limit business endpoints, not actuator
        if (path.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(httpReq);
        TokenBucket bucket = buckets.computeIfAbsent(clientIp,
                k -> new TokenBucket(maxRequests, windowMs));

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            HttpServletResponse httpResp = (HttpServletResponse) response;
            httpResp.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResp.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResp.getWriter().write(
                    "{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Periodic cleanup of expired buckets to prevent unbounded memory growth.
     */
    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void cleanupExpiredBuckets() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(e -> e.getValue().isExpired(now, windowMs * 2));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
