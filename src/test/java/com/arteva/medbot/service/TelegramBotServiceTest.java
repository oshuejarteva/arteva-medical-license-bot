package com.arteva.medbot.service;

import com.arteva.medbot.model.AskResponse;
import com.arteva.medbot.rag.DocumentIngestionService;
import com.arteva.medbot.rag.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramBotServiceTest {

    @Mock
    private RagService ragService;

    @Mock
    private DocumentIngestionService ingestionService;

    private TestTelegramBotService botService;

    private static final long ADMIN_CHAT_ID = 12345L;
    private static final long NON_ADMIN_CHAT_ID = 99999L;

    @BeforeEach
    void setUp() {
        botService = new TestTelegramBotService(
                "test-token", "TestBot", String.valueOf(ADMIN_CHAT_ID),
                ragService, ingestionService);
    }

    @Test
    void onUpdateReceived_withStartCommand_shouldSendWelcome() {
        Update update = createUpdate("/start", ADMIN_CHAT_ID);
        botService.onUpdateReceived(update);

        List<SendMessage> messages = botService.getSentMessages();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getText().contains("Здравствуйте"));
    }

    @Test
    void onUpdateReceived_withHelpCommand_shouldSendHelp() {
        Update update = createUpdate("/help", ADMIN_CHAT_ID);
        botService.onUpdateReceived(update);

        List<SendMessage> messages = botService.getSentMessages();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getText().contains("вопрос"));
    }

    @Test
    void onUpdateReceived_withReindexCommand_asAdmin_shouldCallReindex() {
        Update update = createUpdate("/reindex", ADMIN_CHAT_ID);
        when(ingestionService.reindex()).thenReturn(3);

        botService.onUpdateReceived(update);

        verify(ingestionService).reindex();
        List<SendMessage> messages = botService.getSentMessages();
        assertEquals(2, messages.size());
        assertTrue(messages.get(1).getText().contains("3"));
    }

    @Test
    void onUpdateReceived_withReindexCommand_asNonAdmin_shouldDeny() {
        Update update = createUpdate("/reindex", NON_ADMIN_CHAT_ID);

        botService.onUpdateReceived(update);

        verifyNoInteractions(ingestionService);
        List<SendMessage> messages = botService.getSentMessages();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getText().contains("администраторам"));
    }

    @Test
    void onUpdateReceived_withReindexAlreadyRunning_shouldInform() {
        Update update = createUpdate("/reindex", ADMIN_CHAT_ID);
        when(ingestionService.reindex()).thenThrow(new IllegalStateException("Reindex is already in progress"));

        botService.onUpdateReceived(update);

        List<SendMessage> messages = botService.getSentMessages();
        assertEquals(2, messages.size());
        assertTrue(messages.get(1).getText().contains("уже выполняется"));
    }

    @Test
    void onUpdateReceived_withReindexFailure_shouldSendError() {
        Update update = createUpdate("/reindex", ADMIN_CHAT_ID);
        when(ingestionService.reindex()).thenThrow(new RuntimeException("Connection refused"));

        botService.onUpdateReceived(update);

        List<SendMessage> messages = botService.getSentMessages();
        assertEquals(2, messages.size());
        assertTrue(messages.get(1).getText().contains("Ошибка"));
    }

    @Test
    void onUpdateReceived_withQuestion_shouldCallRagAndReply() {
        String question = "Какие документы нужны для лицензии?";
        Update update = createUpdate(question, ADMIN_CHAT_ID);
        AskResponse response = new AskResponse("Нужен паспорт", List.of("doc1.docx"));
        when(ragService.ask(question)).thenReturn(response);

        botService.onUpdateReceived(update);

        verify(ragService).ask(question);
        List<SendMessage> messages = botService.getSentMessages();
        assertFalse(messages.isEmpty());
        String text = messages.get(messages.size() - 1).getText();
        assertTrue(text.contains("Нужен паспорт"));
        assertTrue(text.contains("doc1.docx"));
    }

    @Test
    void onUpdateReceived_withNoInfoAnswer_shouldReturnStandardMessage() {
        Update update = createUpdate("Неизвестный вопрос", ADMIN_CHAT_ID);
        when(ragService.ask(anyString())).thenReturn(AskResponse.noInfo());

        botService.onUpdateReceived(update);

        List<SendMessage> messages = botService.getSentMessages();
        assertFalse(messages.isEmpty());
        String text = messages.get(messages.size() - 1).getText();
        assertTrue(text.contains("В документах нет информации"));
    }

    @Test
    void onUpdateReceived_withNoMessage_shouldDoNothing() {
        Update update = new Update();

        botService.onUpdateReceived(update);

        verifyNoInteractions(ragService);
        assertTrue(botService.getSentMessages().isEmpty());
    }

    @Test
    void onUpdateReceived_withRagFailure_shouldSendErrorMessage() {
        Update update = createUpdate("Вопрос", ADMIN_CHAT_ID);
        when(ragService.ask(anyString())).thenThrow(new RuntimeException("LLM unavailable"));

        botService.onUpdateReceived(update);

        List<SendMessage> messages = botService.getSentMessages();
        assertFalse(messages.isEmpty());
        assertTrue(messages.get(messages.size() - 1).getText().contains("ошибка"));
    }

    @Test
    void getBotUsername_shouldReturnConfiguredName() {
        assertEquals("TestBot", botService.getBotUsername());
    }

    @Test
    void onUpdateReceived_withMultipleSources_shouldListAll() {
        Update update = createUpdate("Вопрос", ADMIN_CHAT_ID);
        AskResponse response = new AskResponse("Ответ",
                List.of("doc1.docx", "doc2.docx"));
        when(ragService.ask(anyString())).thenReturn(response);

        botService.onUpdateReceived(update);

        List<SendMessage> messages = botService.getSentMessages();
        String text = messages.get(messages.size() - 1).getText();
        assertTrue(text.contains("doc1.docx"));
        assertTrue(text.contains("doc2.docx"));
        assertTrue(text.contains("Источники"));
    }

    @Test
    void parseAdminIds_shouldParseCommaSeparated() {
        Set<Long> ids = TelegramBotService.parseAdminIds("100,200,300");
        assertEquals(Set.of(100L, 200L, 300L), ids);
    }

    @Test
    void parseAdminIds_withEmpty_shouldReturnEmptySet() {
        assertTrue(TelegramBotService.parseAdminIds("").isEmpty());
        assertTrue(TelegramBotService.parseAdminIds(null).isEmpty());
    }

    // --- Helpers ---

    private Update createUpdate(String text, long chatId) {
        Update update = new Update();
        Message message = new Message();
        message.setText(text);
        message.setChat(new Chat(chatId, "private"));

        User user = new User();
        user.setFirstName("TestUser");
        user.setId(chatId);
        user.setIsBot(false);
        message.setFrom(user);
        message.setMessageId(1);

        update.setMessage(message);
        return update;
    }

    /**
     * Testable subclass that captures sent messages instead of calling Telegram API.
     * Uses SyncTaskExecutor so all async operations happen synchronously in tests.
     */
    static class TestTelegramBotService extends TelegramBotService {

        private final java.util.ArrayList<SendMessage> sentMessages = new java.util.ArrayList<>();

        TestTelegramBotService(String token, String username, String adminChatIds,
                               RagService ragService,
                               DocumentIngestionService ingestionService) {
            super(token, username, adminChatIds, ragService, ingestionService, new SyncTaskExecutor());
        }

        @Override
        public <T extends java.io.Serializable, Method extends org.telegram.telegrambots.meta.api.methods.BotApiMethod<T>> T execute(Method method)
                throws TelegramApiException {
            if (method instanceof SendMessage sendMessage) {
                sentMessages.add(sendMessage);
            }
            return null;
        }

        List<SendMessage> getSentMessages() {
            return sentMessages;
        }
    }
}
