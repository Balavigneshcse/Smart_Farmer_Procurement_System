package com.cropcoders.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mobile and Aadhaar digits cannot be changed after registration. */
public record UpdateProfileRequest(
        @NotBlank(message = "Enter your name") @Size(min = 2, max = 100) String name,
        @NotBlank(message = "Enter your address") @Size(max = 255) String address,
        @NotBlank(message = "Enter your district") @Size(max = 60) String district,
        @NotBlank @Pattern(regexp = ValidationPatterns.LANGUAGE, message = "Language must be en, ta or hi") String language) {
}
