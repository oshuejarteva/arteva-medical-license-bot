package com.arteva.medbot.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated application properties.
 * Application will fail to start if required properties are missing.
 */
public final class AppProperties {

    private AppProperties() {
    }

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
