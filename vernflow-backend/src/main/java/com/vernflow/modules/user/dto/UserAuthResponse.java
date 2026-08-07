package com.vernflow.modules.user.dto;

import java.time.LocalDateTime;

public record UserAuthResponse(
        String token,
        Long userId,
        String username,
        String displayName,
        String role,
        LocalDateTime expiresAt
) {
}
