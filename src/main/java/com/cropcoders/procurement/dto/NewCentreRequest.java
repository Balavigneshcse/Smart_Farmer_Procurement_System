package com.cropcoders.procurement.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The super admin adds a procurement centre. The code becomes the token prefix (TNJ-017) and cannot change later. */
public record NewCentreRequest(
        @NotBlank(message = "Enter a centre code")
        @Pattern(regexp = "^[A-Za-z]{2,5}$", message = "The code is 2 to 5 letters, for example KMB") String code,
        @NotBlank(message = "Enter the centre name") @Size(max = 100) String name,
        @NotBlank(message = "Enter the district") @Size(max = 60) String district,
        @NotBlank(message = "Enter the address") @Size(max = 255) String address,
        @NotNull(message = "Enter the opening time") LocalTime openTime,
        @NotNull(message = "Enter the closing time") LocalTime closeTime,
        @NotNull @Min(value = 1, message = "At least 1 counter") @Max(value = 20, message = "At most 20 counters")
        Integer counters,
        @NotNull @Min(value = 1, message = "At least 1 minute") @Max(value = 60, message = "At most 60 minutes")
        Integer avgServiceMin) {
}
