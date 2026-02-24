package com.arteva.medbot.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Сервис индексации документов (Document Ingestion Pipeline).
 * <p>
 * Процесс индексации:
 * <ol>
 *   <li>Чтение DOC/DOCX файлов с диска ({@link DocumentParser})</li>
 *   <li>Разбиение на чанки (1000 токенов с перекрытием 200)</li>
 *   <li>Генерация эмбеддингов через OpenRouter</li>
 *   <li>Сохранение в Qdrant</li>
 * </ol>
 * <p>
 * Меры безопасности при переиндексации:
 * <ul>
 *   <li>{@link ReentrantLock} предотвращает конкурентные деструктивные операции</li>
 *   <li>Документы парсятся <b>до</b> удаления коллекции — при ошибке парсинга данные сохраняются</li>
 * </ul>
 *
 * @see DocumentParser
 * @see QdrantCollectionManager
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private static final int MAX_SEGMENT_SIZE_TOKENS = 1000;
    private static final int OVERLAP_SIZE_TOKENS = 200;

    private final DocumentParser documentParser;
    private final EmbeddingModel embeddingModel;
    private final QdrantEmbeddingStore embeddingStore;
    private final QdrantCollectionManager collectionManager;
    private final String docsPath;
    private final EmbeddingStoreIngestor ingestor;
    private final ReentrantLock reindexLock = new ReentrantLock();

    public DocumentIngestionService(
            DocumentParser documentParser,
            EmbeddingModel embeddingModel,
            QdrantEmbeddingStore embeddingStore,
            QdrantCollectionManager collectionManager,
            @Value("${app.docs-path}") String docsPath,
            @Value("${app.tokenizer-model:gpt-4o}") String tokenizerModel) {
        this.documentParser = documentParser;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.collectionManager = collectionManager;
        this.docsPath = docsPath;

        this.ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(
                        MAX_SEGMENT_SIZE_TOKENS,
                        OVERLAP_SIZE_TOKENS,
                        new OpenAiTokenizer(tokenizerModel)))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    /**
     * Индексирует все DOC/DOCX-документы из настроенной директории.
     * <p>
     * Добавляет эмбеддинги к существующей коллекции без удаления старых.
     *
     * @return количество проиндексированных документов
     */
    public int ingest() {
        log.info("Starting document ingestion from: {}", docsPath);

        List<Document> documents = documentParser.parseAll(docsPath);
        if (documents.isEmpty()) {
            log.warn("No documents found to ingest");
            return 0;
        }

        ingestor.ingest(documents);

        log.info("Successfully ingested {} documents", documents.size());
        return documents.size();
    }

    /**
     * Полная переиндексация: удаляет коллекцию, пересоздаёт и повторно индексирует.
     * <p>
     * Меры безопасности:
     * <ul>
     *   <li>Non-reentrant lock предотвращает параллельные вызовы</li>
     *   <li>Документы парсятся <b>ДО</b> удаления коллекции</li>
     *   <li>При ошибке парсинга существующие данные сохраняются</li>
     * </ul>
     *
     * @return количество переиндексированных документов
     * @throws IllegalStateException если переиндексация уже выполняется
     */
    public int reindex() {
        if (!reindexLock.tryLock()) {
            throw new IllegalStateException("Reindex is already in progress");
        }
        try {
            log.info("Starting full reindex...");

            // 1. Parse all documents FIRST — if this fails, existing data is preserved
            List<Document> documents = documentParser.parseAll(docsPath);
            if (documents.isEmpty()) {
                log.warn("No documents found. Skipping reindex to preserve existing data.");
                return 0;
            }

            log.info("Parsed {} documents. Recreating collection and ingesting...", documents.size());

            // 2. Documents validated — safe to recreate collection
            collectionManager.recreateCollection();

            // 3. Ingest the already-parsed documents
            ingestor.ingest(documents);

            log.info("Reindex completed. {} documents indexed.", documents.size());
            return documents.size();
        } finally {
            reindexLock.unlock();
        }
    }
}
