package com.cropcoders.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.MOBILE, message = "Enter a valid 10-digit mobile number") String mobile,
        @NotBlank @Pattern(regexp = ValidationPatterns.OTP, message = "Enter the 6-digit OTP") String otp) {
}
