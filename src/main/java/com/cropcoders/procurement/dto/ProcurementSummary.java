package com.cropcoders.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cropcoders.procurement.entity.Procurement;

/** The weighment result shown to the farmer. */
public record ProcurementSummary(
        BigDecimal weightQtl,
        BigDecimal moisturePct,
        String grade,
        BigDecimal ratePerQtl,
        BigDecimal amount,
        boolean accepted,
        String remarks,
        LocalDateTime recordedAt) {

    public static ProcurementSummary from(Procurement p) {
        return new ProcurementSummary(p.getWeightQtl(), p.getMoisturePct(), p.getGrade(), p.getRatePerQtl(),
                p.getAmount(), p.isAccepted(), p.getRemarks(), p.getRecordedAt());
    }
}
