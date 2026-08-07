package com.vernflow.modules.tourism.dto;

import java.util.List;

public record TravelConversationResponse(
        String reply,
        TravelDraft draft,
        List<FieldChange> changes,
        List<String> missingFields,
        List<String> warnings,
        boolean readyToGenerate,
        String engine
) {

    public record FieldChange(String field, String label, String value) {
    }
}
