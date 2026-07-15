package com.vernflow.modules.user.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String username,
        String displayName,
        LocalDateTime expiresAt
) {
}
