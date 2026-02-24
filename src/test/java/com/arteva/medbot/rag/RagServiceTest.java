package com.arteva.medbot.rag;

import com.arteva.medbot.model.AskResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private ChatLanguageModel chatModel;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private QdrantEmbeddingStore embeddingStore;

    @Captor
    private ArgumentCaptor<List<ChatMessage>> messagesCaptor;

    private RagService ragService;

    private static final int TOP_K = 6;
    private static final double SIMILARITY_THRESHOLD = 0.75;

    @BeforeEach
    void setUp() {
        ragService = new RagService(chatModel, embeddingModel, embeddingStore,
                TOP_K, SIMILARITY_THRESHOLD);
    }

    @Test
    void ask_withNoRelevantDocuments_shouldReturnNoInfo() {
        // given
        String question = "Какие документы нужны?";
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f, 0.2f});

        when(embeddingModel.embed(question)).thenReturn(new Response<>(queryEmbedding));
        when(embeddingStore.findRelevant(eq(queryEmbedding), eq(TOP_K), eq(SIMILARITY_THRESHOLD)))
                .thenReturn(Collections.emptyList());

        // when
        AskResponse response = ragService.ask(question);

        // then
        assertEquals("В документах нет информации по данному вопросу.", response.answer());
        assertTrue(response.sources().isEmpty());
        verifyNoInteractions(chatModel); // LLM should NOT be called
    }

    @Test
    void ask_withRelevantDocuments_shouldCallLlmAndReturnAnswer() {
        // given
        String question = "Какие документы нужны?";
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f, 0.2f});

        TextSegment segment1 = TextSegment.from("Нужен паспорт",
                dev.langchain4j.data.document.Metadata.from("source", "doc1.docx"));
        TextSegment segment2 = TextSegment.from("Нужен диплом",
                dev.langchain4j.data.document.Metadata.from("source", "doc2.docx"));

        EmbeddingMatch<TextSegment> match1 = new EmbeddingMatch<>(0.9, "id1", queryEmbedding, segment1);
        EmbeddingMatch<TextSegment> match2 = new EmbeddingMatch<>(0.85, "id2", queryEmbedding, segment2);

        when(embeddingModel.embed(question)).thenReturn(new Response<>(queryEmbedding));
        when(embeddingStore.findRelevant(eq(queryEmbedding), eq(TOP_K), eq(SIMILARITY_THRESHOLD)))
                .thenReturn(List.of(match1, match2));

        String llmAnswer = "Нужен паспорт (doc1.docx) и диплом (doc2.docx).";
        when(chatModel.generate(anyList()))
                .thenReturn(new Response<>(AiMessage.from(llmAnswer)));

        // when
        AskResponse response = ragService.ask(question);

        // then
        assertEquals(llmAnswer, response.answer());
        assertEquals(List.of("doc1.docx", "doc2.docx"), response.sources());
    }

    @Test
    void ask_shouldPassContextInPrompt() {
        // given
        String question = "Вопрос";
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f});

        TextSegment segment = TextSegment.from("Текст фрагмента",
                dev.langchain4j.data.document.Metadata.from("source", "test.docx"));
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.95, "id1", queryEmbedding, segment);

        when(embeddingModel.embed(question)).thenReturn(new Response<>(queryEmbedding));
        when(embeddingStore.findRelevant(any(), anyInt(), anyDouble()))
                .thenReturn(List.of(match));
        when(chatModel.generate(messagesCaptor.capture()))
                .thenReturn(new Response<>(AiMessage.from("Ответ")));

        // when
        ragService.ask(question);

        // then
        List<ChatMessage> messages = messagesCaptor.getValue();
        assertEquals(2, messages.size()); // system + user

        // Verify system message contains RAG instructions
        String systemText = messages.get(0).toString();
        assertTrue(systemText.contains("Отвечай исключительно на основании"));

        // Verify user message contains the fragment and question
        String userText = messages.get(1).toString();
        assertTrue(userText.contains("Текст фрагмента"));
        assertTrue(userText.contains("Вопрос"));
    }

    @Test
    void ask_withDuplicateSources_shouldDeduplicateThem() {
        // given
        String question = "Вопрос";
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f});

        TextSegment seg1 = TextSegment.from("Фрагмент 1",
                dev.langchain4j.data.document.Metadata.from("source", "same.docx"));
        TextSegment seg2 = TextSegment.from("Фрагмент 2",
                dev.langchain4j.data.document.Metadata.from("source", "same.docx"));

        when(embeddingModel.embed(question)).thenReturn(new Response<>(queryEmbedding));
        when(embeddingStore.findRelevant(any(), anyInt(), anyDouble()))
                .thenReturn(List.of(
                        new EmbeddingMatch<>(0.9, "id1", queryEmbedding, seg1),
                        new EmbeddingMatch<>(0.85, "id2", queryEmbedding, seg2)));
        when(chatModel.generate(anyList()))
                .thenReturn(new Response<>(AiMessage.from("Ответ")));

        // when
        AskResponse response = ragService.ask(question);

        // then — should be only one unique source
        assertEquals(1, response.sources().size());
        assertEquals("same.docx", response.sources().get(0));
    }

    @Test
    void ask_whenLlmFails_shouldReturnErrorMessage() {
        // given
        String question = "Вопрос";
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f});

        TextSegment seg = TextSegment.from("Текст",
                dev.langchain4j.data.document.Metadata.from("source", "doc.docx"));

        when(embeddingModel.embed(question)).thenReturn(new Response<>(queryEmbedding));
        when(embeddingStore.findRelevant(any(), anyInt(), anyDouble()))
                .thenReturn(List.of(new EmbeddingMatch<>(0.9, "id1", queryEmbedding, seg)));
        when(chatModel.generate(anyList()))
                .thenThrow(new RuntimeException("API timeout"));

        // when
        AskResponse response = ragService.ask(question);

        // then
        assertTrue(response.answer().contains("Произошла ошибка"));
        assertEquals(List.of("doc.docx"), response.sources());
    }

    @Test
    void ask_shouldUseConfiguredTopKAndThreshold() {
        // given
        String question = "Вопрос";
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f});

        when(embeddingModel.embed(question)).thenReturn(new Response<>(queryEmbedding));
        when(embeddingStore.findRelevant(eq(queryEmbedding), eq(TOP_K), eq(SIMILARITY_THRESHOLD)))
                .thenReturn(Collections.emptyList());

        // when
        ragService.ask(question);

        // then — verify exact topK and threshold are passed
        verify(embeddingStore).findRelevant(queryEmbedding, TOP_K, SIMILARITY_THRESHOLD);
    }
}
