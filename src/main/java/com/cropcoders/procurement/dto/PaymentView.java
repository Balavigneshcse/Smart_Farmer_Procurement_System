package com.cropcoders.procurement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.cropcoders.procurement.entity.Booking;
import com.cropcoders.procurement.entity.Payment;
import com.cropcoders.procurement.entity.Procurement;
import com.cropcoders.procurement.enums.PaymentStatus;

/** One row on the admin payments page. */
public record PaymentView(
        Long id,
        PaymentStatus status,
        BigDecimal amount,
        String token,
        LocalDate bookingDate,
        String farmerName,
        String farmerMobile,
        String payTo,
        String centreName,
        String cropName,
        BigDecimal weightQtl,
        String grade,
        String rzpOrderId,
        String rzpPaymentId,
        LocalDateTime createdAt,
        LocalDateTime paidAt) {

    /** payTo = where the farmer wants the money (bank or UPI), or null if they have not said yet. */
    public static PaymentView from(Payment payment, String payTo) {
        Procurement procurement = payment.getProcurement();
        Booking booking = procurement.getBooking();
        return new PaymentView(
                payment.getId(),
                payment.getStatus(),
                payment.amountRupees(),
                booking.getToken(),
                booking.getBookingDate(),
                booking.getFarmer().getName(),
                booking.getFarmer().getMobile(),
                payTo,
                booking.getCentre().getName(),
                booking.getCrop().getNameEn(),
                procurement.getWeightQtl(),
                procurement.getGrade(),
                payment.getRzpOrderId(),
                payment.getRzpPaymentId(),
                payment.getCreatedAt(),
                payment.getPaidAt());
    }
}
