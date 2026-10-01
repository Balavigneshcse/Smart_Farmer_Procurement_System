package com.cropcoders.procurement.dto;

/**
 * What the admin's browser needs to open Razorpay Checkout. keyId is the public key id (never the secret).
 * amountPaise is what Razorpay charges; fullAmountPaise is the farmer's real amount. They differ only when
 * the amount is above the test-mode limit (see AppProperties.Razorpay).
 */
public record PaymentOrderResponse(
        Long paymentId,
        String keyId,
        String orderId,
        long amountPaise,
        long fullAmountPaise,
        String currency,
        String description,
        String farmerName,
        String farmerMobile) {
}
