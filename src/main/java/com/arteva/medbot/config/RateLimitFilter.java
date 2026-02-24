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
 * HTTP-фильтр ограничения частоты запросов (rate limiting) по IP-адресу клиента.
 * <p>
 * Реализует алгоритм «tokenBucket с фиксированным окном»:
 * каждому IP даётся N токенов в минуту; при исчерпании — {@code 429 Too Many Requests}.
 * <p>
 * Особенности:
 * <ul>
 *   <li>Эндпоинты {@code /actuator/**} не ограничиваются (для мониторинга)</li>
 *   <li>Поддерживается заголовок {@code X-Forwarded-For} (для работы за reverse-proxy)</li>
 *   <li>Устаревшие бакеты очищаются каждые 5 минут для предотвращения утечек памяти</li>
 * </ul>
 *
 * @see TokenBucket
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
     * Периодическая очистка устаревших бакетов для предотвращения
     * неограниченного роста памяти.
     * <p>
     * Запускается каждые 5 минут. Удаляет бакеты, неактивные более 2 минут.
     */
    @Scheduled(fixedRate = 300_000) // каждые 5 минут
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
