package com.cropcoders.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank(message = "Enter your email address") @Size(max = 254)
        @Pattern(regexp = ValidationPatterns.EMAIL, message = "Enter a valid email address") String email,
        @NotBlank @Pattern(regexp = ValidationPatterns.OTP, message = "Enter the 6-digit OTP") String otp) {
}
