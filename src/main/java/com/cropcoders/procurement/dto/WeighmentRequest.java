package com.cropcoders.procurement.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What the operator records at the counter after weighing and checking the crop. */
public record WeighmentRequest(
        @NotNull(message = "Enter the weight")
        @DecimalMin(value = "0.1", message = "Weight must be at least 0.1 quintal")
        @DecimalMax(value = "1000", message = "Weight can be at most 1000 quintals")
        @Digits(integer = 4, fraction = 2, message = "Use at most 2 decimal places")
        BigDecimal weightQtl,

        @NotNull(message = "Enter the moisture")
        @DecimalMin(value = "0", message = "Moisture cannot be negative")
        @DecimalMax(value = "40", message = "Moisture above 40% is not realistic")
        @Digits(integer = 2, fraction = 1, message = "Use at most 1 decimal place")
        BigDecimal moisturePct,

        @NotBlank @Pattern(regexp = "^[ABC]$", message = "Grade must be A, B or C") String grade,

        @NotNull(message = "Choose accept or reject") Boolean accepted,

        @Size(max = 255) String remarks) {
}
