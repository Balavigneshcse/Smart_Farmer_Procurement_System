package com.cropcoders.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** code = the token typed by the operator (e.g. TNJ-005) or the text read from the farmer's QR code. */
public record CheckInRequest(@NotBlank(message = "Enter a token or scan the QR code") @Size(max = 60) String code) {
}
