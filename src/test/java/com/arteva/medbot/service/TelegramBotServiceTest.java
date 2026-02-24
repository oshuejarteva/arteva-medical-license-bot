package com.arteva.medbot.service;

import com.arteva.medbot.model.AskResponse;
import com.arteva.medbot.rag.DocumentIngestionService;
import com.arteva.medbot.rag.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

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

    @BeforeEach
    void setUp() {
        botService = new TestTelegramBotService(
                "test-token", "TestBot", ragService, ingestionService);
    }

    @Test
    void onUpdateReceived_withStartCommand_shouldSendWelcome() {
        // given
        Update update = createUpdate("/start");

        // when
        botService.onUpdateReceived(update);

        // then
        List<SendMessage> messages = botService.getSentMessages();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getText().contains("Здравствуйте"));
    }

    @Test
    void onUpdateReceived_withHelpCommand_shouldSendHelp() {
        // given
        Update update = createUpdate("/help");

        // when
        botService.onUpdateReceived(update);

        // then
        List<SendMessage> messages = botService.getSentMessages();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).getText().contains("вопрос"));
    }

    @Test
    void onUpdateReceived_withReindexCommand_shouldCallReindex() {
        // given
        Update update = createUpdate("/reindex");
        when(ingestionService.reindex()).thenReturn(3);

        // when
        botService.onUpdateReceived(update);

        // then
        verify(ingestionService).reindex();
        List<SendMessage> messages = botService.getSentMessages();
        // Should send "starting" + "completed" messages
        assertEquals(2, messages.size());
        assertTrue(messages.get(1).getText().contains("3"));
    }

    @Test
    void onUpdateReceived_withReindexFailure_shouldSendError() {
        // given
        Update update = createUpdate("/reindex");
        when(ingestionService.reindex()).thenThrow(new RuntimeException("Connection refused"));

        // when
        botService.onUpdateReceived(update);

        // then
        List<SendMessage> messages = botService.getSentMessages();
        assertEquals(2, messages.size());
        assertTrue(messages.get(1).getText().contains("Ошибка"));
    }

    @Test
    void onUpdateReceived_withQuestion_shouldCallRagAndReply() {
        // given
        String question = "Какие документы нужны для лицензии?";
        Update update = createUpdate(question);
        AskResponse response = new AskResponse("Нужен паспорт", List.of("doc1.docx"));
        when(ragService.ask(question)).thenReturn(response);

        // when
        botService.onUpdateReceived(update);

        // then
        verify(ragService).ask(question);
        List<SendMessage> messages = botService.getSentMessages();
        assertFalse(messages.isEmpty());
        String text = messages.get(messages.size() - 1).getText();
        assertTrue(text.contains("Нужен паспорт"));
        assertTrue(text.contains("doc1.docx"));
    }

    @Test
    void onUpdateReceived_withNoInfoAnswer_shouldReturnStandardMessage() {
        // given
        Update update = createUpdate("Неизвестный вопрос");
        when(ragService.ask(anyString())).thenReturn(AskResponse.noInfo());

        // when
        botService.onUpdateReceived(update);

        // then
        List<SendMessage> messages = botService.getSentMessages();
        assertFalse(messages.isEmpty());
        String text = messages.get(messages.size() - 1).getText();
        assertTrue(text.contains("В документах нет информации"));
    }

    @Test
    void onUpdateReceived_withNoMessage_shouldDoNothing() {
        // given
        Update update = new Update();

        // when
        botService.onUpdateReceived(update);

        // then
        verifyNoInteractions(ragService);
        assertTrue(botService.getSentMessages().isEmpty());
    }

    @Test
    void onUpdateReceived_withRagFailure_shouldSendErrorMessage() {
        // given
        Update update = createUpdate("Вопрос");
        when(ragService.ask(anyString())).thenThrow(new RuntimeException("LLM unavailable"));

        // when
        botService.onUpdateReceived(update);

        // then
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
        // given
        Update update = createUpdate("Вопрос");
        AskResponse response = new AskResponse("Ответ",
                List.of("doc1.docx", "doc2.docx"));
        when(ragService.ask(anyString())).thenReturn(response);

        // when
        botService.onUpdateReceived(update);

        // then
        List<SendMessage> messages = botService.getSentMessages();
        String text = messages.get(messages.size() - 1).getText();
        assertTrue(text.contains("doc1.docx"));
        assertTrue(text.contains("doc2.docx"));
        assertTrue(text.contains("Источники"));
    }

    // --- Helpers ---

    private Update createUpdate(String text) {
        Update update = new Update();
        Message message = new Message();
        message.setText(text);
        message.setChat(new Chat(12345L, "private"));

        User user = new User();
        user.setFirstName("TestUser");
        user.setId(12345L);
        user.setIsBot(false);
        message.setFrom(user);
        message.setMessageId(1);

        update.setMessage(message);
        return update;
    }

    /**
     * Testable subclass that captures sent messages instead of calling Telegram API.
     */
    static class TestTelegramBotService extends TelegramBotService {

        private final java.util.ArrayList<SendMessage> sentMessages = new java.util.ArrayList<>();

        TestTelegramBotService(String token, String username,
                               RagService ragService,
                               DocumentIngestionService ingestionService) {
            super(token, username, ragService, ingestionService);
        }

        @Override
        public <T extends java.io.Serializable, Method extends org.telegram.telegrambots.meta.api.methods.BotApiMethod<T>> T execute(Method method)
                throws TelegramApiException {
            if (method instanceof SendMessage sendMessage) {
                sentMessages.add(sendMessage);
            }
            // Return null for SendChatAction and other non-essential methods
            return null;
        }

        List<SendMessage> getSentMessages() {
            return sentMessages;
        }
    }
}
