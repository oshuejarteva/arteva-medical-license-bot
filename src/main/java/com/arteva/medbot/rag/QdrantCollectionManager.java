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
 * Manages Qdrant collection lifecycle: creation, deletion, recreation.
 * Separated from configuration for SRP compliance.
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
     * Ensures the configured collection exists. Creates it if missing.
     * Called during application startup.
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
     * Drops and recreates the collection. Used during full reindex.
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
