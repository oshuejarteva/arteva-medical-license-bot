package com.arteva.medbot.config;

import com.arteva.medbot.service.TelegramBotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Конфигурация Telegram-бота.
 * <p>
 * Активируется только при {@code telegram.enabled=true} (по умолчанию включена).
 * Регистрирует бота в Telegram API и создаёт пул потоков
 * для асинхронной обработки входящих сообщений.
 * <p>
 * Параметры пула потоков:
 * <ul>
 *   <li>Основные потоки: 4</li>
 *   <li>Максимум потоков: 8</li>
 *   <li>Очередь: 100 задач</li>
 *   <li>Политика переполнения: {@code CallerRunsPolicy} (выполняется в потоке вызывающего)</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = true)
public class TelegramBotConfig {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotConfig.class);

    /**
     * Регистрирует Telegram-бота для получения обновлений через long polling.
     *
     * @param botService экземпляр Telegram-бота
     * @return настроенный Telegram API
     * @throws TelegramApiException при ошибке регистрации
     */
    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBotService botService) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(botService);
        log.info("Telegram bot '{}' registered successfully (long polling)", botService.getBotUsername());
        return api;
    }

    /**
     * Пул потоков для асинхронной обработки Telegram-сообщений.
     * <p>
     * Отдельный пул гарантирует, что обработка сообщений
     * не блокирует поток long polling.
     *
     * @return настроенный {@link TaskExecutor}
     */
    @Bean
    public TaskExecutor telegramTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("tg-handler-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
