package com.arteva.medbot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Запрос к RAG-пайплайну через REST API.
 * <p>
 * Используется как тело {@code POST /ask}. Содержит текстовый вопрос пользователя,
 * который будет обработан через семантический поиск и LLM.
 *
 * @param question текст вопроса пользователя. Не может быть пустым.
 *                 Максимальная длина — 4 000 символов (защита от злоупотреблений).
 */
public record AskRequest(

        @NotBlank(message = "Question must not be blank")
        @Size(max = 4000, message = "Question must not exceed 4000 characters")
        String question
) {
}
