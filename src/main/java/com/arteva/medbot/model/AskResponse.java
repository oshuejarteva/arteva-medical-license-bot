package com.arteva.medbot.model;

import java.util.List;

public record AskResponse(
        String answer,
        List<String> sources
) {

    public static AskResponse noInfo() {
        return new AskResponse(
                "В документах нет информации по данному вопросу.",
                List.of()
        );
    }
}
