package com.cropcoders.procurement.dto;

/** What the admin's browser needs to open Razorpay Checkout. keyId is the public key id (never the secret). */
public record PaymentOrderResponse(
        Long paymentId,
        String keyId,
        String orderId,
        long amountPaise,
        String currency,
        String description,
        String farmerName,
        String farmerMobile) {
}
