package com.arteva.medbot.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AskRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void validQuestion_shouldHaveNoViolations() {
        AskRequest request = new AskRequest("Какие документы нужны?");
        var violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void blankQuestion_shouldHaveViolation() {
        AskRequest request = new AskRequest("");
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullQuestion_shouldHaveViolation() {
        AskRequest request = new AskRequest(null);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void whitespaceQuestion_shouldHaveViolation() {
        AskRequest request = new AskRequest("   ");
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void accessors_shouldWork() {
        AskRequest request = new AskRequest("Вопрос");
        assertEquals("Вопрос", request.question());
    }
}
