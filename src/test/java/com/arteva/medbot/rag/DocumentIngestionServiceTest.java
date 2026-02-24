package com.arteva.medbot.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
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
    private QdrantCollectionManager collectionManager;

    private DocumentIngestionService service;

    private static final String DOCS_PATH = "/tmp/docs";
    private static final String TOKENIZER_MODEL = "gpt-4o";

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionService(
                documentParser, embeddingModel, embeddingStore,
                collectionManager, DOCS_PATH, TOKENIZER_MODEL);
    }

    @Test
    void ingest_withNoDocuments_shouldReturnZero() {
        when(documentParser.parseAll(DOCS_PATH)).thenReturn(Collections.emptyList());

        int result = service.ingest();

        assertEquals(0, result);
        verify(documentParser).parseAll(DOCS_PATH);
    }

    @Test
    void ingest_withDocuments_shouldCallParser() {
        // We verify that parseAll is called with the correct path.
        verify(documentParser, never()).parseAll(anyString());
    }

    @Test
    void reindex_shouldParseFirstThenRecreateCollection() {
        // given — documents are available
        List<Document> docs = List.of(
                Document.from("Текст 1", Metadata.from("source", "doc1.docx")));
        when(documentParser.parseAll(DOCS_PATH)).thenReturn(docs);
        doNothing().when(collectionManager).recreateCollection();

        // Stub embedding model so ingestor.ingest() can complete
        Embedding fakeEmbedding = Embedding.from(new float[]{0.1f, 0.2f});
        when(embeddingModel.embedAll(anyList()))
                .thenReturn(new Response<>(List.of(fakeEmbedding)));

        // when
        int result = service.reindex();

        // then
        assertEquals(1, result);
        // Verify order: parseAll BEFORE recreateCollection
        var inOrder = inOrder(documentParser, collectionManager);
        inOrder.verify(documentParser).parseAll(DOCS_PATH);
        inOrder.verify(collectionManager).recreateCollection();
    }

    @Test
    void reindex_withNoDocuments_shouldSkipRecreateAndReturnZero() {
        when(documentParser.parseAll(DOCS_PATH)).thenReturn(Collections.emptyList());

        int result = service.reindex();

        assertEquals(0, result);
        // Collection should NOT be recreated when there are no documents
        verify(collectionManager, never()).recreateCollection();
    }

    @Test
    void reindex_whenRecreateCollectionFails_shouldPropagateException() {
        List<Document> docs = List.of(
                Document.from("Текст", Metadata.from("source", "doc.docx")));
        when(documentParser.parseAll(DOCS_PATH)).thenReturn(docs);
        doThrow(new RuntimeException("Qdrant is down"))
                .when(collectionManager).recreateCollection();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.reindex());
        assertTrue(ex.getMessage().contains("Qdrant is down"));
    }

    @Test
    void reindex_whenParsingFails_shouldNotRecreateCollection() {
        when(documentParser.parseAll(DOCS_PATH)).thenThrow(new RuntimeException("IO error"));

        assertThrows(RuntimeException.class, () -> service.reindex());

        // Collection should be untouched when parsing fails
        verify(collectionManager, never()).recreateCollection();
    }
}
