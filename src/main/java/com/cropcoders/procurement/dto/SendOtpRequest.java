package com.cropcoders.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** language = the page language, used for the OTP email of a farmer who is not registered yet. */
public record SendOtpRequest(
        @NotBlank(message = "Enter your email address") @Size(max = 254)
        @Pattern(regexp = ValidationPatterns.EMAIL, message = "Enter a valid email address") String email,
        @Pattern(regexp = ValidationPatterns.LANGUAGE, message = "Language must be en, ta or hi") String language) {
}
