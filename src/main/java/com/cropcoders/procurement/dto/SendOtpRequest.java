package com.cropcoders.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** language = the page language, used for the OTP SMS of a farmer who is not registered yet. */
public record SendOtpRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.MOBILE, message = "Enter a valid 10-digit mobile number") String mobile,
        @Pattern(regexp = ValidationPatterns.LANGUAGE, message = "Language must be en, ta or hi") String language) {
}
