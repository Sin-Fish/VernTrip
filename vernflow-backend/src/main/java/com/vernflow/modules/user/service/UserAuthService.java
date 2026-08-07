package com.vernflow.modules.user.service;

import com.vernflow.common.config.UserAuthProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.user.domain.AppUser;
import com.vernflow.modules.user.domain.AppUser.Role;
import com.vernflow.modules.user.dto.UserAuthResponse;
import com.vernflow.modules.user.dto.UserLoginRequest;
import com.vernflow.modules.user.dto.UserProfileResponse;
import com.vernflow.modules.user.repository.AppUserRepository;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class UserAuthService {

    public static final String REQUEST_ATTR_USER_ID = "vernflow.user.id";
    public static final String REQUEST_ATTR_USERNAME = "vernflow.user.username";
    public static final String REQUEST_ATTR_DISPLAY_NAME = "vernflow.user.displayName";
    public static final String REQUEST_ATTR_ROLE = "vernflow.user.role";
    public static final String REQUEST_ATTR_EXPIRES_AT = "vernflow.user.expiresAt";

    private static final List<SeedUser> DEFAULT_USERS = List.of(
            new SeedUser("demo", "demo123", "游客演示账号", Role.TOURIST),
            new SeedUser("merchant", "merchant123", "商家演示账号", Role.MERCHANT),
            new SeedUser("reviewer", "reviewer123", "Reviewer", Role.INTERNAL)
    );

    private final UserAuthProperties userAuthProperties;
    private final AppUserRepository appUserRepository;
    private final ConcurrentMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    public UserAuthService(
            UserAuthProperties userAuthProperties,
            AppUserRepository appUserRepository
    ) {
        this.userAuthProperties = userAuthProperties;
        this.appUserRepository = appUserRepository;
    }

    @PostConstruct
    @Transactional
    public void ensureDefaultUsers() {
        for (SeedUser seedUser : DEFAULT_USERS) {
            AppUser user = appUserRepository.findByUsernameIgnoreCase(seedUser.username())
                    .orElseGet(() -> AppUser.create(
                            seedUser.username(),
                            hashPassword(seedUser.password()),
                            seedUser.displayName(),
                            seedUser.role()
                    ));
            user.updateIdentity(seedUser.displayName(), seedUser.role());
            appUserRepository.save(user);
        }
    }

    public UserAuthResponse login(UserLoginRequest request) {
        AppUser user = appUserRepository.findByUsernameIgnoreCase(request.username().trim())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new BusinessException("用户名或密码错误。"));

        if (!hashPassword(request.password()).equals(user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误。");
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plus(userAuthProperties.sessionTtl());
        sessions.put(token, new UserSession(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRole().name(), expiresAt
        ));
        return new UserAuthResponse(
                token, user.getId(), user.getUsername(), user.getDisplayName(), user.getRole().name(), expiresAt
        );
    }

    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            sessions.remove(token);
        }
    }

    public UserSession resolveSession(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }

        UserSession session = sessions.get(token);
        if (session == null) {
            return null;
        }

        if (session.expiresAt().isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            return null;
        }

        UserSession refreshed = new UserSession(
                session.userId(),
                session.username(),
                session.displayName(),
                session.role(),
                LocalDateTime.now().plus(userAuthProperties.sessionTtl())
        );
        sessions.put(token, refreshed);
        return refreshed;
    }

    public String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        String headerToken = request.getHeader("X-User-Token");
        if (StringUtils.hasText(headerToken)) {
            return headerToken;
        }
        return request.getParameter("token");
    }

    public Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(REQUEST_ATTR_USER_ID);
        if (userId instanceof Long id) {
            return id;
        }
        return defaultUser().getId();
    }

    public String currentUsername(HttpServletRequest request) {
        Object username = request.getAttribute(REQUEST_ATTR_USERNAME);
        if (username instanceof String text && !text.isBlank()) {
            return text;
        }
        return defaultUser().getUsername();
    }

    public UserProfileResponse currentProfile(HttpServletRequest request) {
        AppUser fallback = defaultUser();
        Object userId = request.getAttribute(REQUEST_ATTR_USER_ID);
        Object username = request.getAttribute(REQUEST_ATTR_USERNAME);
        Object displayName = request.getAttribute(REQUEST_ATTR_DISPLAY_NAME);
        Object role = request.getAttribute(REQUEST_ATTR_ROLE);
        Object expiresAt = request.getAttribute(REQUEST_ATTR_EXPIRES_AT);
        return new UserProfileResponse(
                userId instanceof Long id ? id : fallback.getId(),
                username instanceof String text ? text : fallback.getUsername(),
                displayName instanceof String text ? text : fallback.getDisplayName(),
                role instanceof String text ? text : fallback.getRole().name(),
                expiresAt instanceof LocalDateTime time ? time : null
        );
    }

    public List<UserSummary> listUsers() {
        return appUserRepository.findAllByOrderByIdAsc().stream()
                .map(user -> new UserSummary(user.getId(), user.getUsername(), user.getDisplayName()))
                .toList();
    }

    public AppUser requireUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User was not found: " + userId));
    }

    private AppUser defaultUser() {
        return appUserRepository.findByUsernameIgnoreCase("demo")
                .orElseGet(() -> appUserRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new BusinessException("No product users are available.")));
    }

    private String hashPassword(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private record SeedUser(String username, String password, String displayName, Role role) {
    }

    public record UserSession(Long userId, String username, String displayName, String role, LocalDateTime expiresAt) {
    }

    public record UserSummary(Long id, String username, String displayName) {
    }
}
