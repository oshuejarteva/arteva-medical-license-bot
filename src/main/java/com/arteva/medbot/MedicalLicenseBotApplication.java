package com.arteva.medbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа приложения Medical License Bot.
 * <p>
 * Бот предоставляет интерфейс (REST API и Telegram) для ответов
 * на вопросы пользователей по медицинскому лицензированию,
 * используя RAG (Retrieval-Augmented Generation) поверх загруженных документов.
 * <p>
 * Включённые возможности:
 * <ul>
 *   <li>{@code @EnableAsync} — асинхронная обработка Telegram-сообщений</li>
 *   <li>{@code @EnableScheduling} — периодическая очистка rate-limit бакетов</li>
 *   <li>{@code @ConfigurationPropertiesScan} — автоматическое связывание YAML-свойств с record-классами</li>
 * </ul>
 *
 * @author Arteva
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan("com.arteva.medbot.config")
public class MedicalLicenseBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedicalLicenseBotApplication.class, args);
    }
}
