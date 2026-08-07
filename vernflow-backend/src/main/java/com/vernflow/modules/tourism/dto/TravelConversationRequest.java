package com.vernflow.modules.tourism.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TravelConversationRequest(
        @NotBlank @Size(max = 1000) String prompt,
        @Valid TravelDraft current,
        @Size(max = 8) List<@Valid HistoryMessage> history
) {

    public record HistoryMessage(
            @NotBlank @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 1000) String text
    ) {
    }
}
