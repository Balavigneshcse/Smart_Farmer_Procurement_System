package com.cropcoders.procurement.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull(message = "Choose a time slot") Long slotId,
        @NotNull(message = "Choose the crop") Long cropId,
        @NotNull(message = "Enter the quantity")
        @DecimalMin(value = "0.5", message = "Quantity must be at least 0.5 quintal")
        @DecimalMax(value = "500", message = "Quantity can be at most 500 quintals")
        @Digits(integer = 3, fraction = 2, message = "Use at most 2 decimal places")
        BigDecimal estQtyQtl) {
}
