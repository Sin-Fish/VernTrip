package com.vernflow.modules.admin.dto;

import java.time.LocalDateTime;

public record AdminAuthProfileResponse(String username, LocalDateTime expiresAt) {
}
