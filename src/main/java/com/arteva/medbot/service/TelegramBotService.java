package com.arteva.medbot.service;

import com.arteva.medbot.model.AskResponse;
import com.arteva.medbot.rag.DocumentIngestionService;
import com.arteva.medbot.rag.RagService;
import com.arteva.medbot.util.TokenBucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Telegram bot that receives user messages via long polling
 * and answers questions using the RAG pipeline.
 * <p>
 * Message processing is dispatched to a thread pool to avoid blocking the polling thread.
 * /reindex command requires the caller's chatId to be in the admin whitelist.
 */
@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = true)
public class TelegramBotService extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    static final int TELEGRAM_MAX_MESSAGE_LENGTH = 4096;

    static final String CMD_START = "/start";
    static final String CMD_HELP = "/help";
    static final String CMD_REINDEX = "/reindex";

    private static final int TG_RATE_LIMIT_PER_MINUTE = 10;
    private static final long TG_RATE_WINDOW_MS = 60_000L;

    private final RagService ragService;
    private final DocumentIngestionService ingestionService;
    private final String botUsername;
    private final Set<Long> adminChatIds;
    private final TaskExecutor taskExecutor;
    private final ConcurrentHashMap<Long, TokenBucket> rateLimits = new ConcurrentHashMap<>();

    public TelegramBotService(
            @Value("${telegram.token}") String botToken,
            @Value("${telegram.username:MedLicenseBot}") String botUsername,
            @Value("${telegram.admin-chat-ids:}") String adminChatIdsStr,
            RagService ragService,
            DocumentIngestionService ingestionService,
            @Qualifier("telegramTaskExecutor") TaskExecutor taskExecutor) {
        super(botToken);
        this.botUsername = botUsername;
        this.ragService = ragService;
        this.ingestionService = ingestionService;
        this.taskExecutor = taskExecutor;
        this.adminChatIds = parseAdminIds(adminChatIdsStr);

        if (adminChatIds.isEmpty()) {
            log.warn("No Telegram admin chat IDs configured (telegram.admin-chat-ids). "
                    + "/reindex command will be unavailable via Telegram.");
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        taskExecutor.execute(() -> processUpdate(update));
    }

    void processUpdate(Update update) {
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();
        String userName = update.getMessage().getFrom().getFirstName();

        log.debug("Received message from {} (chatId={}): {}", userName, chatId,
                text.length() > 100 ? text.substring(0, 100) + "..." : text);

        // Rate limit non-command messages
        if (!text.startsWith("/") && !isRateAllowed(chatId)) {
            sendText(chatId, "⏳ Слишком много запросов. Пожалуйста, подождите.");
            return;
        }

        try {
            handleMessage(chatId, text);
        } catch (Exception e) {
            log.error("Error processing message from chatId={}: {}", chatId, e.getMessage(), e);
            sendText(chatId, "Произошла ошибка при обработке запроса. Попробуйте позже.");
        }
    }

    private boolean isRateAllowed(long chatId) {
        TokenBucket bucket = rateLimits.computeIfAbsent(chatId,
                k -> new TokenBucket(TG_RATE_LIMIT_PER_MINUTE, TG_RATE_WINDOW_MS));
        return bucket.tryConsume();
    }

    private void handleMessage(long chatId, String text) {
        switch (text) {
            case CMD_START -> sendText(chatId,
                    "Здравствуйте! Я бот для поиска информации в документах.\n\n"
                    + "Просто напишите ваш вопрос, и я найду ответ в базе документов.\n\n"
                    + "Команды:\n"
                    + "/reindex — переиндексация документов (только для администраторов)\n"
                    + "/help — справка");

            case CMD_HELP -> sendText(chatId,
                    "Отправьте мне любой вопрос — я найду ответ в загруженных документах.\n\n"
                    + "Если информации нет — сообщу об этом.\n"
                    + "Все ответы строго на основе документов.");

            case CMD_REINDEX -> handleReindex(chatId);

            default -> handleQuestion(chatId, text);
        }
    }

    private void handleReindex(long chatId) {
        if (!adminChatIds.contains(chatId)) {
            sendText(chatId, "⛔ Команда /reindex доступна только администраторам.");
            return;
        }

        sendText(chatId, "⏳ Запускаю переиндексацию документов...");
        try {
            int count = ingestionService.reindex();
            sendText(chatId, "✅ Переиндексация завершена. Обработано документов: " + count);
        } catch (IllegalStateException e) {
            sendText(chatId, "⚠️ Переиндексация уже выполняется. Дождитесь завершения.");
        } catch (Exception e) {
            log.error("Reindex failed via Telegram", e);
            sendText(chatId, "❌ Ошибка при переиндексации.");
        }
    }

    private void handleQuestion(long chatId, String question) {
        sendTypingAction(chatId);

        AskResponse response = ragService.ask(question);

        StringBuilder sb = new StringBuilder();
        sb.append(response.answer());

        if (response.sources() != null && !response.sources().isEmpty()) {
            sb.append("\n\n📄 Источники: ");
            sb.append(String.join(", ", response.sources()));
        }

        sendText(chatId, sb.toString());
    }

    private void sendTypingAction(long chatId) {
        try {
            execute(SendChatAction.builder()
                    .chatId(String.valueOf(chatId))
                    .action(ActionType.TYPING.toString())
                    .build());
        } catch (TelegramApiException e) {
            log.debug("Failed to send typing action to chatId={}", chatId);
        }
    }

    void sendText(long chatId, String text) {
        // Telegram limits messages to 4096 characters; split if needed
        for (int i = 0; i < text.length(); i += TELEGRAM_MAX_MESSAGE_LENGTH) {
            String chunk = text.substring(i,
                    Math.min(i + TELEGRAM_MAX_MESSAGE_LENGTH, text.length()));
            try {
                execute(SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text(chunk)
                        .build());
            } catch (TelegramApiException e) {
                log.error("Failed to send message to chatId={}", chatId, e);
            }
        }
    }

    static Set<Long> parseAdminIds(String str) {
        if (str == null || str.isBlank()) {
            return Collections.emptySet();
        }
        try {
            return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NumberFormatException e) {
            log.error("Invalid telegram.admin-chat-ids format: '{}'. Expected comma-separated numbers.", str);
            return Collections.emptySet();
        }
    }
}
