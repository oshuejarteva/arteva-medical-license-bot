package com.arteva.medbot.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AskResponseTest {

    @Test
    void noInfo_shouldReturnStandardMessage() {
        AskResponse response = AskResponse.noInfo();

        assertEquals("В документах нет информации по данному вопросу.", response.answer());
        assertNotNull(response.sources());
        assertTrue(response.sources().isEmpty());
    }

    @Test
    void constructor_shouldStoreValues() {
        AskResponse response = new AskResponse("Ответ", List.of("doc1.docx", "doc2.docx"));

        assertEquals("Ответ", response.answer());
        assertEquals(2, response.sources().size());
        assertEquals("doc1.docx", response.sources().get(0));
        assertEquals("doc2.docx", response.sources().get(1));
    }

    @Test
    void constructor_withEmptySources_shouldWork() {
        AskResponse response = new AskResponse("Ответ", List.of());

        assertEquals("Ответ", response.answer());
        assertTrue(response.sources().isEmpty());
    }

    @Test
    void constructor_withNullAnswer_shouldAccept() {
        AskResponse response = new AskResponse(null, List.of());

        assertNull(response.answer());
    }
}
