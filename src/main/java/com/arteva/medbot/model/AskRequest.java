package com.arteva.medbot.model;

import jakarta.validation.constraints.NotBlank;

public record AskRequest(

        @NotBlank(message = "Question must not be blank")
        String question
) {
}
