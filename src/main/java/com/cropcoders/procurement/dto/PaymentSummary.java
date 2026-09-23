package com.cropcoders.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cropcoders.procurement.entity.Payment;
import com.cropcoders.procurement.enums.PaymentStatus;

/** Payment status shown to the farmer. reference = Razorpay payment id once paid. */
public record PaymentSummary(PaymentStatus status, BigDecimal amount, String reference, LocalDateTime paidAt) {

    public static PaymentSummary from(Payment p) {
        return new PaymentSummary(p.getStatus(), p.amountRupees(), p.getRzpPaymentId(), p.getPaidAt());
    }
}
