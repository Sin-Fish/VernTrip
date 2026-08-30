package com.vernflow.modules.tourism.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MerchantContentGenerateResponse(
        String sourceName,
        String channel,
        String content,
        String engine,
        List<String> facts,
        LocalDateTime generatedAt
) {
}
