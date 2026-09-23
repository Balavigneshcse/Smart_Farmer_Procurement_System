package com.cropcoders.procurement.dto;

import java.math.BigDecimal;
import java.util.List;

public record PaymentListResponse(List<PaymentView> payments, Summary summary, boolean razorpayConfigured) {

    public record Summary(int pendingCount, BigDecimal pendingAmount, int paidTodayCount, BigDecimal paidTodayAmount,
                          BigDecimal paidTotalAmount) {
    }
}
