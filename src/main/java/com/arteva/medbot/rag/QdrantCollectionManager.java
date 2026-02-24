package com.arteva.medbot.rag;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Менеджер жизненного цикла коллекции Qdrant.
 * <p>
 * Отвечает за:
 * <ul>
 *   <li>Создание коллекции при первом запуске ({@link #ensureCollectionExists()})</li>
 *   <li>Пересоздание коллекции при полной переиндексации ({@link #recreateCollection()})</li>
 * </ul>
 * <p>
 * Выделен из конфигурации для соблюдения принципа единой ответственности (SRP).
 * Использует try-with-resources для корректного закрытия gRPC-клиента.
 */
@Service
public class QdrantCollectionManager {

    private static final Logger log = LoggerFactory.getLogger(QdrantCollectionManager.class);

    private final String host;
    private final int port;
    private final String collectionName;
    private final int dimension;

    public QdrantCollectionManager(
            @Value("${qdrant.host}") String host,
            @Value("${qdrant.port}") int port,
            @Value("${qdrant.collection-name}") String collectionName,
            @Value("${qdrant.dimension}") int dimension) {
        this.host = host;
        this.port = port;
        this.collectionName = collectionName;
        this.dimension = dimension;
    }

    /**
     * Проверяет наличие коллекции и создаёт её при отсутствии.
     * <p>
     * Вызывается при старте приложения. При ошибке подключения к Qdrant
     * выбрасывает {@link IllegalStateException} (fail-fast).
     *
     * @throws IllegalStateException если невозможно создать/проверить коллекцию
     */
    public void ensureCollectionExists() {
        try (QdrantClient client = createClient()) {
            var collections = client.listCollectionsAsync().get(10, TimeUnit.SECONDS);

            boolean exists = collections.stream()
                    .anyMatch(c -> c.equals(collectionName));

            if (!exists) {
                client.createCollectionAsync(collectionName,
                        Collections.VectorParams.newBuilder()
                                .setSize(dimension)
                                .setDistance(Collections.Distance.Cosine)
                                .build()
                ).get(10, TimeUnit.SECONDS);

                log.info("Created Qdrant collection '{}' with dimension {}", collectionName, dimension);
            } else {
                log.info("Qdrant collection '{}' already exists", collectionName);
            }
        } catch (Exception e) {
            log.error("Failed to initialize Qdrant collection '{}': {}", collectionName, e.getMessage());
            throw new IllegalStateException("Cannot initialize Qdrant collection", e);
        }
    }

    /**
     * Удаляет и пересоздаёт коллекцию.
     * <p>
     * Используется при полной переиндексации.
     * Если коллекция не существует — просто создаёт новую.
     *
     * @throws RuntimeException при ошибке подключения к Qdrant
     */
    public void recreateCollection() {
        try (QdrantClient client = createClient()) {
            try {
                client.deleteCollectionAsync(collectionName)
                        .get(10, TimeUnit.SECONDS);
                log.info("Deleted Qdrant collection '{}'", collectionName);
            } catch (Exception e) {
                log.debug("Collection '{}' did not exist, nothing to delete", collectionName);
            }

            client.createCollectionAsync(collectionName,
                    Collections.VectorParams.newBuilder()
                            .setSize(dimension)
                            .setDistance(Collections.Distance.Cosine)
                            .build()
            ).get(10, TimeUnit.SECONDS);

            log.info("Recreated Qdrant collection '{}' with dimension {}", collectionName, dimension);
        } catch (Exception e) {
            log.error("Failed to recreate Qdrant collection: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to recreate Qdrant collection", e);
        }
    }

    private QdrantClient createClient() {
        return new QdrantClient(
                QdrantGrpcClient.newBuilder(host, port, false).build());
    }
}
