package com.cropcoders.procurement.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin adds or edits a crop. Names are shown to farmers in their language. */
public record CropRequest(
        @NotBlank(message = "Enter the English name") @Size(max = 60) String nameEn,
        @NotBlank(message = "Enter the Tamil name") @Size(max = 60) String nameTa,
        @NotBlank(message = "Enter the Hindi name") @Size(max = 60) String nameHi,

        @NotNull(message = "Enter the rate")
        @DecimalMin(value = "1", message = "Rate must be at least Rs 1")
        @DecimalMax(value = "100000", message = "Rate can be at most Rs 1,00,000")
        @Digits(integer = 6, fraction = 2, message = "Use at most 2 decimal places")
        BigDecimal ratePerQtl,

        @NotNull(message = "Enter the moisture limit")
        @DecimalMin(value = "1", message = "Moisture limit must be at least 1%")
        @DecimalMax(value = "40", message = "Moisture limit can be at most 40%")
        @Digits(integer = 2, fraction = 1, message = "Use at most 1 decimal place")
        BigDecimal maxMoisturePct,

        boolean active) {
}
