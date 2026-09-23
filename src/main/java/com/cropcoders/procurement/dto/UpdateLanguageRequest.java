package com.cropcoders.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateLanguageRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.LANGUAGE, message = "Language must be en, ta or hi") String language) {
}
