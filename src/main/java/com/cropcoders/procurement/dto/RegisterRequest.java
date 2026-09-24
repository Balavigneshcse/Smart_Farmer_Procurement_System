package com.cropcoders.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The email address comes from the registration token (it was verified with the OTP). */
public record RegisterRequest(
        @NotBlank(message = "Verification token is missing") String registrationToken,
        @NotBlank(message = "Enter your name") @Size(min = 2, max = 100) String name,
        @NotBlank @Pattern(regexp = ValidationPatterns.MOBILE, message = "Enter a valid 10-digit mobile number") String mobile,
        @NotBlank(message = "Enter your address") @Size(max = 255) String address,
        @NotBlank(message = "Enter your district") @Size(max = 60) String district,
        @NotBlank @Pattern(regexp = ValidationPatterns.AADHAAR_LAST4, message = "Enter exactly 4 digits") String aadhaarLast4,
        @NotBlank @Pattern(regexp = ValidationPatterns.LANGUAGE, message = "Language must be en, ta or hi") String language) {
}
