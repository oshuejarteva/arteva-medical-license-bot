package com.arteva.medbot.rag;

import com.arteva.medbot.model.AskResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис RAG (Retrieval-Augmented Generation).
 * <p>
 * Основной пайплайн обработки вопросов:
 * <ol>
 *   <li>Генерация эмбеддинга вопроса пользователя</li>
 *   <li>Семантический поиск релевантных фрагментов в Qdrant</li>
 *   <li>Формирование промпта с контекстом из документов</li>
 *   <li>Вызов LLM через OpenRouter</li>
 *   <li>Возврат ответа со списком источников</li>
 * </ol>
 * <p>
 * Если релевантных фрагментов не найдено — возвращает {@link AskResponse#noInfo()}.
 * При ошибке LLM — возвращает ответ с сообщением об ошибке.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final String SYSTEM_PROMPT = """
            Отвечай исключительно на основании предоставленных фрагментов документов.
            Если информации недостаточно — ответь:
            'В документах нет информации по данному вопросу.'
            Указывай источник (название файла) для каждого утверждения.
            Отвечай на русском языке.
            """;

    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final QdrantEmbeddingStore embeddingStore;
    private final int topK;
    private final double similarityThreshold;

    public RagService(
            ChatLanguageModel chatModel,
            EmbeddingModel embeddingModel,
            QdrantEmbeddingStore embeddingStore,
            @Value("${rag.top-k:6}") int topK,
            @Value("${rag.similarity-threshold:0.75}") double similarityThreshold) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * Обрабатывает вопрос пользователя через RAG-пайплайн.
     *
     * @param question текст вопроса пользователя
     * @return ответ с текстом и списком источников
     */
    public AskResponse ask(String question) {
        log.debug("Processing question: {}", question.length() > 200 ? question.substring(0, 200) + "..." : question);

        // 1. Embed the question
        Embedding queryEmbedding = embeddingModel.embed(question).content();

        // 2. Search for relevant chunks
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
                queryEmbedding, topK, similarityThreshold);

        // Filter out matches with null embedded segments
        List<EmbeddingMatch<TextSegment>> validMatches = matches.stream()
                .filter(m -> m.embedded() != null)
                .toList();

        if (validMatches.isEmpty()) {
            log.info("No relevant documents found for the question");
            return AskResponse.noInfo();
        }

        log.info("Found {} relevant chunks (threshold={})", validMatches.size(), similarityThreshold);

        // 3. Extract context and sources
        String context = buildContext(validMatches);
        List<String> sources = extractSources(validMatches);

        // 4. Call LLM with context
        String answer = callLlm(context, question);
        if (answer == null) {
            return new AskResponse(
                    "Произошла ошибка при генерации ответа. Попробуйте позже.", List.of());
        }

        log.info("Generated answer from {} sources: {}", sources.size(), sources);
        return new AskResponse(answer, sources);
    }

    private String buildContext(List<EmbeddingMatch<TextSegment>> matches) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            String source = match.embedded().metadata().getString("source");
            String text = match.embedded().text();

            sb.append("--- Фрагмент ").append(i + 1);
            if (source != null) {
                sb.append(" (источник: ").append(source).append(")");
            }
            sb.append(" ---\n");
            sb.append(text).append("\n\n");
        }
        return sb.toString().strip();
    }

    private List<String> extractSources(List<EmbeddingMatch<TextSegment>> matches) {
        Set<String> sources = matches.stream()
                .filter(m -> m.embedded() != null)
                .map(m -> m.embedded().metadata().getString("source"))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(sources);
    }

    private String callLlm(String context, String question) {
        String userPrompt = """
                Фрагменты документов:
                
                %s
                
                Вопрос пользователя: %s
                """.formatted(context, question);

        List<ChatMessage> messages = List.of(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(userPrompt)
        );

        try {
            Response<AiMessage> response = chatModel.generate(messages);
            return response.content().text();
        } catch (Exception e) {
            log.error("LLM call failed", e);
            return null;
        }
    }
}
