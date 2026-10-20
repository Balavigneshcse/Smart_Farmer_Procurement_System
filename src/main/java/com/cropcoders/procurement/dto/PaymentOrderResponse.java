package com.cropcoders.procurement.dto;

/**
 * What the admin's browser needs to open Razorpay Checkout. keyId is the public key id (never the secret).
 * amountPaise is what Razorpay charges; fullAmountPaise is the farmer's real amount. They differ only when
 * the amount is above the test-mode limit (see AppProperties.Razorpay).
 *
 * alreadyPaid: Razorpay says this order was already paid (an earlier Checkout succeeded, but its result never
 * reached us). The payment has now been marked PAID with reference rzpPaymentId, so the browser must not
 * open Checkout again. rzpPaymentId is null unless alreadyPaid is true.
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
        String farmerMobile,
        boolean alreadyPaid,
        String rzpPaymentId) {
}
