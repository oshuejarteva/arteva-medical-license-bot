package com.arteva.medbot.controller;

import com.arteva.medbot.model.AskRequest;
import com.arteva.medbot.model.AskResponse;
import com.arteva.medbot.rag.DocumentIngestionService;
import com.arteva.medbot.rag.RagService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);

    private final RagService ragService;
    private final DocumentIngestionService ingestionService;

    public AskController(RagService ragService, DocumentIngestionService ingestionService) {
        this.ragService = ragService;
        this.ingestionService = ingestionService;
    }

    /**
     * POST /ask
     * Accepts a question and returns an answer based on indexed documents.
     */
    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        log.info("REST /ask — question length: {}", request.question().length());
        AskResponse response = ragService.ask(request.question());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /reindex
     * Re-reads all documents from disk, re-creates embeddings, and stores in Qdrant.
     * Requires ADMIN role (Basic Auth).
     */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex() {
        log.info("REST /reindex — starting reindexing");
        try {
            int count = ingestionService.reindex();
            return ResponseEntity.ok(Map.of(
                    "status", "completed",
                    "documentsIndexed", count
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "status", "rejected",
                            "message", "Reindex is already in progress"
                    ));
        }
    }
}
