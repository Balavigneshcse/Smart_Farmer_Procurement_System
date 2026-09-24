package com.cropcoders.procurement.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin edits a centre. The code (token prefix) and slot length cannot be changed. */
public record CentreRequest(
        @NotBlank(message = "Enter the centre name") @Size(max = 100) String name,
        @NotBlank(message = "Enter the district") @Size(max = 60) String district,
        @NotBlank(message = "Enter the address") @Size(max = 255) String address,
        @NotNull(message = "Enter the opening time") LocalTime openTime,
        @NotNull(message = "Enter the closing time") LocalTime closeTime,
        @NotNull @Min(value = 1, message = "At least 1 counter") @Max(value = 20, message = "At most 20 counters")
        Integer counters,
        @NotNull @Min(value = 1, message = "At least 1 minute") @Max(value = 60, message = "At most 60 minutes")
        Integer avgServiceMin,
        boolean active) {
}
