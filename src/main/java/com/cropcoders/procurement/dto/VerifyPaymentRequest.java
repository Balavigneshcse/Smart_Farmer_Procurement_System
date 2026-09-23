package com.cropcoders.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The three values Razorpay Checkout gives the browser after a successful payment. */
public record VerifyPaymentRequest(
        @NotBlank @Size(max = 40) String razorpayOrderId,
        @NotBlank @Size(max = 40) String razorpayPaymentId,
        @NotBlank @Size(max = 128) String razorpaySignature) {
}
