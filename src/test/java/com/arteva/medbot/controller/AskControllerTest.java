package com.arteva.medbot.controller;

import com.arteva.medbot.model.AskResponse;
import com.arteva.medbot.rag.DocumentIngestionService;
import com.arteva.medbot.rag.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AskController.class, GlobalExceptionHandler.class})
class AskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RagService ragService;

    @MockBean
    private DocumentIngestionService ingestionService;

    @Test
    void ask_withValidQuestion_shouldReturnAnswer() throws Exception {
        // given
        AskResponse response = new AskResponse("Ответ на вопрос", List.of("doc1.docx"));
        when(ragService.ask("Какие документы нужны?")).thenReturn(response);

        // when / then
        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Какие документы нужны?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Ответ на вопрос"))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0]").value("doc1.docx"));
    }

    @Test
    void ask_withNoRelevantDocs_shouldReturnNoInfoAnswer() throws Exception {
        // given
        when(ragService.ask(anyString())).thenReturn(AskResponse.noInfo());

        // when / then
        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Неизвестный вопрос"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("В документах нет информации по данному вопросу."))
                .andExpect(jsonPath("$.sources", hasSize(0)));
    }

    @Test
    void ask_withBlankQuestion_shouldReturn400() throws Exception {
        // when / then
        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": ""}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ragService);
    }

    @Test
    void ask_withMissingQuestion_shouldReturn400() throws Exception {
        // when / then
        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ragService);
    }

    @Test
    void ask_withMultipleSources_shouldReturnAll() throws Exception {
        // given
        AskResponse response = new AskResponse("Ответ",
                List.of("doc1.docx", "doc2.docx", "doc3.docx"));
        when(ragService.ask(anyString())).thenReturn(response);

        // when / then
        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Вопрос"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", hasSize(3)));
    }

    @Test
    void reindex_shouldReturnStatus() throws Exception {
        // given
        when(ingestionService.reindex()).thenReturn(5);

        // when / then
        mockMvc.perform(post("/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.documentsIndexed").value(5));
    }

    @Test
    void reindex_withZeroDocuments_shouldReturnZero() throws Exception {
        // given
        when(ingestionService.reindex()).thenReturn(0);

        // when / then
        mockMvc.perform(post("/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.documentsIndexed").value(0));
    }

    @Test
    void reindex_whenServiceFails_shouldReturn500() throws Exception {
        // given
        when(ingestionService.reindex()).thenThrow(new RuntimeException("Qdrant unavailable"));

        // when / then
        mockMvc.perform(post("/reindex"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void ask_withWrongHttpMethod_shouldReturn405() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/ask"))
                .andExpect(status().isMethodNotAllowed());
    }
}
