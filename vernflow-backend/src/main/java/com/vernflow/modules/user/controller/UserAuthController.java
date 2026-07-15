package com.vernflow.modules.user.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.user.dto.UserAuthResponse;
import com.vernflow.modules.user.dto.UserLoginRequest;
import com.vernflow.modules.user.dto.UserProfileResponse;
import com.vernflow.modules.user.service.UserAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/auth")
public class UserAuthController {

    private final UserAuthService userAuthService;

    public UserAuthController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<UserAuthResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return ApiResponse.success(userAuthService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        userAuthService.logout(userAuthService.extractToken(request));
        return ApiResponse.success("logged out", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(HttpServletRequest request) {
        return ApiResponse.success(userAuthService.currentProfile(request));
    }
}
