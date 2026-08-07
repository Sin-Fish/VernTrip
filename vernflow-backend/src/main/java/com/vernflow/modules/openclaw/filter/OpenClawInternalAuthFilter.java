package com.vernflow.modules.openclaw.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.config.OpenClawProperties;
import com.vernflow.common.response.ApiResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class OpenClawInternalAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PREFIX = "/api/internal/openclaw/";
    private static final String SERVICE_TOKEN_HEADER = "X-VernTrip-Service-Token";

    private final OpenClawProperties openClawProperties;
    private final ObjectMapper objectMapper;

    public OpenClawInternalAuthFilter(OpenClawProperties openClawProperties, ObjectMapper objectMapper) {
        this.openClawProperties = openClawProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(INTERNAL_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String configuredToken = openClawProperties.serviceToken();
        String suppliedToken = extractToken(request);
        if (!constantTimeEquals(configuredToken, suppliedToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.failure("OpenClaw service authentication failed.")
            ));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization)
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return request.getHeader(SERVICE_TOKEN_HEADER);
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(supplied)) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        );
    }
}
