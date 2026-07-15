package com.vernflow.modules.admin.dto;

import java.time.LocalDateTime;

public record AdminLoginResponse(String token, String username, LocalDateTime expiresAt) {
}
