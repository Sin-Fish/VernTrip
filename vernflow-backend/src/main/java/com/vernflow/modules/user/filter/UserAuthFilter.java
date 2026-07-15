package com.vernflow.modules.user.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.config.UserAuthProperties;
import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.user.service.UserAuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class UserAuthFilter extends OncePerRequestFilter {

    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/workspace",
            "/api/finder",
            "/api/assistant",
            "/api/workbench"
    );

    private final UserAuthProperties userAuthProperties;
    private final UserAuthService userAuthService;
    private final ObjectMapper objectMapper;

    public UserAuthFilter(
            UserAuthProperties userAuthProperties,
            UserAuthService userAuthService,
            ObjectMapper objectMapper
    ) {
        this.userAuthProperties = userAuthProperties;
        this.userAuthService = userAuthService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!userAuthProperties.authRequired()) {
            return true;
        }

        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }

        if (path.startsWith("/api/user/auth")) {
            return path.equals("/api/user/auth/login");
        }

        return PROTECTED_PREFIXES.stream().noneMatch(path::startsWith);
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

        String token = userAuthService.extractToken(request);
        UserAuthService.UserSession session = userAuthService.resolveSession(token);
        if (session == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure("登录已失效，请重新登录。")));
            return;
        }

        request.setAttribute(UserAuthService.REQUEST_ATTR_USER_ID, session.userId());
        request.setAttribute(UserAuthService.REQUEST_ATTR_USERNAME, session.username());
        request.setAttribute(UserAuthService.REQUEST_ATTR_DISPLAY_NAME, session.displayName());
        request.setAttribute(UserAuthService.REQUEST_ATTR_EXPIRES_AT, session.expiresAt());
        filterChain.doFilter(request, response);
    }
}
