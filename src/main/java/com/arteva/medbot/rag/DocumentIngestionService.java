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

/**
 * Handles document ingestion pipeline:
 * 1. Read DOC/DOCX from disk
 * 2. Split into token-based chunks (1000 tokens, 200 overlap)
 * 3. Create embeddings via OpenRouter
 * 4. Store in Qdrant
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
     * Ingests all DOC/DOCX documents from the configured docs directory.
     *
     * @return number of documents ingested
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
     * Drops the vector collection, recreates it, and re-ingests all documents.
     *
     * @return number of documents re-ingested
     */
    public int reindex() {
        log.info("Starting full reindex...");
        collectionManager.recreateCollection();
        int count = ingest();
        log.info("Reindex completed. {} documents indexed.", count);
        return count;
    }
}
