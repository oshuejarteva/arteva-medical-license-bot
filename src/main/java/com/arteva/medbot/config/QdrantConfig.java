package com.arteva.medbot.config;

import com.arteva.medbot.rag.QdrantCollectionManager;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация векторного хранилища Qdrant.
 * <p>
 * При старте приложения:
 * <ol>
 *   <li>Проверяет наличие коллекции в Qdrant (создаёт, если отсутствует)</li>
 *   <li>Создаёт бин {@link QdrantEmbeddingStore} для работы с эмбеддингами</li>
 * </ol>
 *
 * @see QdrantCollectionManager
 */
@Configuration
public class QdrantConfig {

    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);

    /**
     * Создаёт хранилище эмбеддингов и гарантирует наличие коллекции.
     *
     * @param host              хост Qdrant
     * @param port              gRPC-порт Qdrant
     * @param collectionName    имя коллекции
     * @param collectionManager менеджер жизненного цикла коллекции
     * @return настроенное хранилище эмбеддингов
     */
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
