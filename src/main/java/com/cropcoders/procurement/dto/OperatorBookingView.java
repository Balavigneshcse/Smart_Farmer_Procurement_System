package com.cropcoders.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.cropcoders.procurement.enums.BookingStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/** A booking as the operator sees it: includes the farmer's name and Aadhaar last 4 digits to check identity. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperatorBookingView(
        Long id,
        String token,
        BookingStatus status,
        String farmerName,
        String mobile,
        String aadhaarLast4,
        String cropName,
        BigDecimal estQtyQtl,
        LocalTime slotStart,
        LocalTime slotEnd,
        LocalDateTime checkedInAt,
        LocalDateTime calledAt,
        Integer counterNo,
        boolean late,
        Integer position,
        Integer etaMinutes) {
}
