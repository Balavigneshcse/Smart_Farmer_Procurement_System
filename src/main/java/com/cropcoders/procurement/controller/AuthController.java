package com.cropcoders.procurement.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cropcoders.procurement.dto.AuthResponse;
import com.cropcoders.procurement.dto.LoginRequest;
import com.cropcoders.procurement.dto.RegisterRequest;
import com.cropcoders.procurement.dto.SendOtpRequest;
import com.cropcoders.procurement.dto.SendOtpResponse;
import com.cropcoders.procurement.dto.UserResponse;
import com.cropcoders.procurement.dto.VerifyOtpRequest;
import com.cropcoders.procurement.dto.VerifyOtpResponse;
import com.cropcoders.procurement.security.AuthUser;
import com.cropcoders.procurement.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/otp/send")
    public SendOtpResponse sendOtp(@Valid @RequestBody SendOtpRequest request) {
        return authService.sendOtp(request);
    }

    @PostMapping("/otp/verify")
    public VerifyOtpResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return authService.verifyOtp(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.staffLogin(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return authService.currentUser(user);
    }
}
