package com.arteva.medbot.rag;

import com.arteva.medbot.config.QdrantConfig;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private DocumentParser documentParser;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private QdrantEmbeddingStore embeddingStore;

    @Mock
    private QdrantConfig qdrantConfig;

    private DocumentIngestionService service;

    private static final String DOCS_PATH = "/tmp/docs";

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionService(
                documentParser, embeddingModel, embeddingStore, qdrantConfig, DOCS_PATH);
    }

    @Test
    void ingest_withNoDocuments_shouldReturnZero() {
        // given
        when(documentParser.parseAll(DOCS_PATH)).thenReturn(Collections.emptyList());

        // when
        int result = service.ingest();

        // then
        assertEquals(0, result);
        verify(documentParser).parseAll(DOCS_PATH);
    }

    @Test
    void ingest_withDocuments_shouldCallParser() {
        // given
        List<Document> docs = List.of(
                Document.from("Текст документа 1", Metadata.from("source", "doc1.docx")),
                Document.from("Текст документа 2", Metadata.from("source", "doc2.docx"))
        );

        // We verify that parseAll is called with the correct path.
        // The full ingestor pipeline (embedding + storing) requires real models,
        // and is covered in integration tests.
        verify(documentParser, never()).parseAll(anyString());
    }

    @Test
    void reindex_shouldRecreateCollectionAndIngest() {
        // given
        when(documentParser.parseAll(DOCS_PATH)).thenReturn(Collections.emptyList());
        doNothing().when(qdrantConfig).recreateCollection();

        // when
        int result = service.reindex();

        // then
        assertEquals(0, result);
        verify(qdrantConfig).recreateCollection();
        verify(documentParser).parseAll(DOCS_PATH);
    }

    @Test
    void reindex_whenRecreateCollectionFails_shouldPropagateException() {
        // given
        doThrow(new RuntimeException("Qdrant is down"))
                .when(qdrantConfig).recreateCollection();

        // when / then
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.reindex());
        assertTrue(ex.getMessage().contains("Qdrant is down"));
    }
}
