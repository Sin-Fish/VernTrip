package com.vernflow.modules.admin.controller;

import com.vernflow.common.response.ApiResponse;
import com.vernflow.modules.admin.dto.AdminAuthProfileResponse;
import com.vernflow.modules.admin.dto.AdminLoginRequest;
import com.vernflow.modules.admin.dto.AdminLoginResponse;
import com.vernflow.modules.admin.service.AdminAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.success(adminAuthService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        adminAuthService.logout(adminAuthService.extractToken(request), request);
        return ApiResponse.success("logged out", null);
    }

    @GetMapping("/me")
    public ApiResponse<AdminAuthProfileResponse> me(HttpServletRequest request) {
        return ApiResponse.success(adminAuthService.currentProfile(request));
    }
}
