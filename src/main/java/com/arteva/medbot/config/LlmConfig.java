package com.arteva.medbot.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Конфигурация LLM и Embedding-моделей через OpenRouter.
 * <p>
 * Создаёт Spring-бины для:
 * <ul>
 *   <li>{@link ChatLanguageModel} — чат-модель для генерации ответов (120с таймаут, 2 повтора)</li>
 *   <li>{@link EmbeddingModel} — модель эмбеддингов для векторизации текста (60с таймаут, 2 повтора)</li>
 * </ul>
 * <p>
 * Обе модели используют OpenAI-совместимый API через LangChain4j.
 * Логирование запросов/ответов отключено во избежание утечки данных.
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    /**
     * Создаёт чат-модель для генерации ответов на вопросы пользователей.
     *
     * @param apiKey      API-ключ OpenRouter
     * @param baseUrl     базовый URL API
     * @param model       идентификатор модели
     * @param temperature температура генерации
     * @return сконфигурированная чат-модель
     */
    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${openrouter.api-key}") String apiKey,
            @Value("${openrouter.base-url}") String baseUrl,
            @Value("${openrouter.model}") String model,
            @Value("${openrouter.temperature:0.1}") double temperature) {

        log.info("Initializing ChatLanguageModel: baseUrl={}, model={}, temperature={}",
                baseUrl, model, temperature);

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(model)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(120))
                .maxRetries(2)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /**
     * Создаёт модель для генерации эмбеддингов (векторных представлений текста).
     *
     * @param apiKey             API-ключ OpenRouter
     * @param baseUrl            базовый URL API
     * @param embeddingModelName идентификатор embedding-модели
     * @return сконфигурированная embedding-модель
     */
    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${openrouter.api-key}") String apiKey,
            @Value("${openrouter.base-url}") String baseUrl,
            @Value("${openrouter.embedding-model}") String embeddingModelName) {

        log.info("Initializing EmbeddingModel: baseUrl={}, model={}", baseUrl, embeddingModelName);

        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                .build();
    }
}
