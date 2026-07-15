package com.vernflow.modules.admin.service;

import com.vernflow.common.config.AdminProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.admin.dto.AdminAuthProfileResponse;
import com.vernflow.modules.admin.dto.AdminLoginRequest;
import com.vernflow.modules.admin.dto.AdminLoginResponse;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AdminAuthService {

    public static final String REQUEST_ATTR_USERNAME = "vernflow.admin.username";
    public static final String REQUEST_ATTR_EXPIRES_AT = "vernflow.admin.expiresAt";

    private final AdminProperties adminProperties;
    private final AdminOperationLogService adminOperationLogService;
    private final ConcurrentMap<String, AdminSession> sessions = new ConcurrentHashMap<>();

    public AdminAuthService(
            AdminProperties adminProperties,
            AdminOperationLogService adminOperationLogService
    ) {
        this.adminProperties = adminProperties;
        this.adminOperationLogService = adminOperationLogService;
    }

    public AdminLoginResponse login(AdminLoginRequest request) {
        if (!adminProperties.username().equals(request.username())
                || !adminProperties.password().equals(request.password())) {
            throw new BusinessException("Admin username or password is invalid.");
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plus(adminProperties.sessionTtl());
        sessions.put(token, new AdminSession(request.username(), expiresAt));
        adminOperationLogService.log(request.username(), "LOGIN", "auth", null, "Admin console login");
        return new AdminLoginResponse(token, request.username(), expiresAt);
    }

    public void logout(String token, HttpServletRequest request) {
        String username = currentUsername(request);
        if (StringUtils.hasText(token)) {
            sessions.remove(token);
        }
        adminOperationLogService.log(username, "LOGOUT", "auth", null, "Admin console logout");
    }

    public AdminSession requireSession(String token) {
        AdminSession session = resolveSession(token);
        if (session == null) {
            throw new BusinessException("Admin authentication required.");
        }
        return session;
    }

    public AdminSession resolveSession(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }

        AdminSession session = sessions.get(token);
        if (session == null) {
            return null;
        }

        if (session.expiresAt().isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            return null;
        }

        AdminSession refreshed = new AdminSession(session.username(), LocalDateTime.now().plus(adminProperties.sessionTtl()));
        sessions.put(token, refreshed);
        return refreshed;
    }

    public String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return request.getHeader("X-Admin-Token");
    }

    public String currentUsername(HttpServletRequest request) {
        Object username = request.getAttribute(REQUEST_ATTR_USERNAME);
        if (username instanceof String text && !text.isBlank()) {
            return text;
        }
        return adminProperties.username();
    }

    public AdminAuthProfileResponse currentProfile(HttpServletRequest request) {
        Object username = request.getAttribute(REQUEST_ATTR_USERNAME);
        Object expiresAt = request.getAttribute(REQUEST_ATTR_EXPIRES_AT);
        return new AdminAuthProfileResponse(
                username instanceof String text ? text : adminProperties.username(),
                expiresAt instanceof LocalDateTime time ? time : null
        );
    }

    public record AdminSession(String username, LocalDateTime expiresAt) {
    }
}
