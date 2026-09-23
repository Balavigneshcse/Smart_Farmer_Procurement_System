package com.cropcoders.procurement.dto;

import jakarta.validation.constraints.NotBlank;

/** Staff (operator / admin) login. */
public record LoginRequest(
        @NotBlank(message = "Enter your username") String username,
        @NotBlank(message = "Enter your password") String password) {
}
