package com.arteva.medbot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(

        @NotBlank(message = "Question must not be blank")
        @Size(max = 4000, message = "Question must not exceed 4000 characters")
        String question
) {
}
