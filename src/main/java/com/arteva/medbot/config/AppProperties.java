package com.arteva.medbot.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Типобезопасные конфигурационные свойства приложения.
 * <p>
 * Каждое вложенное {@code record}-свойство привязано к соответствующему
 * префиксу в {@code application.yml}. Валидация выполняется при старте —
 * приложение не запустится, если обязательные свойства отсутствуют.
 *
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 */
public final class AppProperties {

    private AppProperties() {
    }

    /**
     * Свойства для подключения к OpenRouter (OpenAI-совместимый API).
     * <p>
     * Привязка: {@code openrouter.*} в {@code application.yml}.
     *
     * @param apiKey         API-ключ OpenRouter (обязателен)
     * @param baseUrl        базовый URL API (по умолчанию: {@code https://openrouter.ai/api/v1})
     * @param model          идентификатор чат-модели для генерации ответов
     *                       (например, {@code google/gemini-2.0-flash-001})
     * @param embeddingModel идентификатор модели для генерации эмбеддингов
     *                       (например, {@code openai/text-embedding-3-small})
     * @param temperature    температура генерации (0.0–1.0). Низкие значения
     *                       дают более детерминированные ответы.
     */
    @Validated
    @ConfigurationProperties(prefix = "openrouter")
    public record OpenRouterProperties(
            @NotBlank(message = "openrouter.api-key is required") String apiKey,
            @NotBlank(message = "openrouter.base-url is required") String baseUrl,
            @NotBlank(message = "openrouter.model is required") String model,
            @NotBlank(message = "openrouter.embedding-model is required") String embeddingModel,
            double temperature
    ) {
    }

    /**
     * Свойства для подключения к Qdrant (векторная база данных).
     * <p>
     * Привязка: {@code qdrant.*} в {@code application.yml}.
     *
     * @param host           хост Qdrant-сервера (при Docker — имя сервиса)
     * @param port           gRPC-порт Qdrant (стандартный: {@code 6334})
     * @param collectionName имя коллекции для хранения эмбеддингов документов
     * @param dimension      размерность вектора. Должна совпадать с embedding-моделью
     *                       ({@code text-embedding-3-small} → 1536)
     */
    @Validated
    @ConfigurationProperties(prefix = "qdrant")
    public record QdrantProperties(
            @NotBlank(message = "qdrant.host is required") String host,
            @Positive(message = "qdrant.port must be positive") int port,
            @NotBlank(message = "qdrant.collection-name is required") String collectionName,
            @Positive(message = "qdrant.dimension must be positive") int dimension
    ) {
    }
}
