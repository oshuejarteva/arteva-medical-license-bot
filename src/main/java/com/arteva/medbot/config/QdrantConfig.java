package com.arteva.medbot.config;

import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class QdrantConfig {

    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);

    @Value("${qdrant.host}")
    private String host;

    @Value("${qdrant.port}")
    private int port;

    @Value("${qdrant.collection-name}")
    private String collectionName;

    @Value("${qdrant.dimension}")
    private int dimension;

    @Bean
    public QdrantEmbeddingStore embeddingStore() {
        ensureCollectionExists();

        log.info("Connecting to Qdrant: host={}, port={}, collection={}",
                host, port, collectionName);

        return QdrantEmbeddingStore.builder()
                .host(host)
                .port(port)
                .collectionName(collectionName)
                .build();
    }

    private void ensureCollectionExists() {
        try {
            QdrantClient client = new QdrantClient(
                    QdrantGrpcClient.newBuilder(host, port, false).build());
            try {
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
            } finally {
                client.close();
            }
        } catch (Exception e) {
            log.warn("Could not initialize Qdrant collection '{}': {}", collectionName, e.getMessage());
        }
    }

    /**
     * Recreates the Qdrant collection (used during reindex).
     */
    public void recreateCollection() {
        try {
            QdrantClient client = new QdrantClient(
                    QdrantGrpcClient.newBuilder(host, port, false).build());
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

            client.close();
        } catch (Exception e) {
            log.error("Failed to recreate Qdrant collection: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to recreate Qdrant collection", e);
        }
    }
}
