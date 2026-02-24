package com.arteva.medbot.config;

import com.arteva.medbot.rag.QdrantCollectionManager;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Qdrant vector store configuration.
 * Creates the EmbeddingStore bean and ensures the collection exists on startup.
 */
@Configuration
public class QdrantConfig {

    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);

    @Bean
    public QdrantEmbeddingStore embeddingStore(
            @Value("${qdrant.host}") String host,
            @Value("${qdrant.port}") int port,
            @Value("${qdrant.collection-name}") String collectionName,
            QdrantCollectionManager collectionManager) {

        collectionManager.ensureCollectionExists();

        log.info("Connecting to Qdrant: host={}, port={}, collection={}", host, port, collectionName);

        return QdrantEmbeddingStore.builder()
                .host(host)
                .port(port)
                .collectionName(collectionName)
                .build();
    }
}
