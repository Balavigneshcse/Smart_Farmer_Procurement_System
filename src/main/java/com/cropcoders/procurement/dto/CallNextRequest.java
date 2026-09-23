package com.cropcoders.procurement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CallNextRequest(@NotNull(message = "Choose a counter") @Min(1) Integer counterNo) {
}
