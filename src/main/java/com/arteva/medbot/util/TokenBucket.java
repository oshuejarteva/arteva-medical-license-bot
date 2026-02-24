package com.arteva.medbot.util;

/**
 * Потокобезопасный алгоритм «token bucket» с фиксированным временным окном.
 * <p>
 * Принцип работы:
 * <ul>
 *   <li>В начале каждого временного окна токены восстанавливаются до полной ёмкости</li>
 *   <li>Каждый вызов {@link #tryConsume()} уменьшает счётчик на 1</li>
 *   <li>При исчерпании токенов возвращается {@code false}</li>
 * </ul>
 * <p>
 * Используется в {@link com.arteva.medbot.config.RateLimitFilter}
 * и {@link com.arteva.medbot.service.TelegramBotService} для ограничения частоты запросов.
 */
public class TokenBucket {

    private final int capacity;
    private final long windowMs;
    private int tokens;
    private long windowStart;

    /**
     * Создаёт новый бакет с указанной ёмкостью и длительностью окна.
     *
     * @param capacity максимальное количество токенов за одно окно
     * @param windowMs длительность окна в миллисекундах
     */
    public TokenBucket(int capacity, long windowMs) {
        this.capacity = capacity;
        this.windowMs = windowMs;
        this.tokens = capacity;
        this.windowStart = System.currentTimeMillis();
    }

    /**
     * Попытка потребить один токен.
     * <p>
     * Если временное окно истекло — счётчик сбрасывается до полной ёмкости.
     *
     * @return {@code true}, если токен был доступен; {@code false}, если лимит исчерпан
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
     * Проверяет, является ли бакет устаревшим (неактивным).
     * <p>
     * Используется для периодической очистки коллекции бакетов.
     *
     * @param now             текущее время в миллисекундах
     * @param idleThresholdMs порог неактивности в миллисекундах
     * @return {@code true}, если бакет неактивен дольше порога
     */
    public synchronized boolean isExpired(long now, long idleThresholdMs) {
        return now - windowStart > idleThresholdMs;
    }
}
