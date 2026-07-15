package com.vernflow.modules.admin.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.admin.service.AdminAuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private final AdminAuthService adminAuthService;
    private final ObjectMapper objectMapper;

    public AdminAuthFilter(
            AdminAuthService adminAuthService,
            ObjectMapper objectMapper
    ) {
        this.adminAuthService = adminAuthService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null
                || !path.startsWith("/api/admin")
                || path.equals("/api/admin/auth/login");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = adminAuthService.extractToken(request);
        AdminAuthService.AdminSession session = adminAuthService.resolveSession(token);
        if (session == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure("Admin authentication required.")));
            return;
        }

        request.setAttribute(AdminAuthService.REQUEST_ATTR_USERNAME, session.username());
        request.setAttribute(AdminAuthService.REQUEST_ATTR_EXPIRES_AT, session.expiresAt());
        filterChain.doFilter(request, response);
    }
}
