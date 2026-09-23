package com.cropcoders.procurement.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.cropcoders.procurement.entity.Booking;
import com.cropcoders.procurement.entity.Centre;
import com.cropcoders.procurement.entity.Payment;
import com.cropcoders.procurement.entity.Procurement;
import com.cropcoders.procurement.enums.BookingStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A booking as the farmer sees it. "events" (the timeline) is only filled for the detail view.
 * qrText is what the QR code contains; the operator scans it at check-in (M4).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
        Long id,
        String token,
        BookingStatus status,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        CentreSummary centre,
        CropResponse crop,
        BigDecimal estQtyQtl,
        BigDecimal estAmount,
        boolean cancellable,
        String qrText,
        LocalDateTime createdAt,
        List<BookingEventResponse> events,
        ProcurementSummary procurement,
        PaymentSummary payment) {

    public record CentreSummary(Long id, String code, String name, String address) {

        static CentreSummary from(Centre centre) {
            return new CentreSummary(centre.getId(), centre.getCode(), centre.getName(), centre.getAddress());
        }
    }

    public static BookingResponse from(Booking booking, LocalDateTime now, List<BookingEventResponse> events) {
        return from(booking, now, events, null, null);
    }

    public static BookingResponse from(Booking booking, LocalDateTime now, List<BookingEventResponse> events,
                                       Procurement procurement, Payment payment) {
        BigDecimal estAmount = booking.getEstQtyQtl()
                .multiply(booking.getCrop().getRatePerQtl())
                .setScale(2, RoundingMode.HALF_UP);
        return new BookingResponse(
                booking.getId(),
                booking.getToken(),
                booking.getStatus(),
                booking.getBookingDate(),
                booking.getSlot().getStartTime(),
                booking.getSlot().getEndTime(),
                CentreSummary.from(booking.getCentre()),
                CropResponse.from(booking.getCrop()),
                booking.getEstQtyQtl(),
                estAmount,
                booking.isCancellable(now),
                qrText(booking),
                booking.getCreatedAt(),
                events,
                procurement == null ? null : ProcurementSummary.from(procurement),
                payment == null ? null : PaymentSummary.from(payment));
    }

    /** "SFPS:<booking id>:<token>" - the id makes a QR from another day unusable. */
    public static String qrText(Booking booking) {
        return "SFPS:" + booking.getId() + ":" + booking.getToken();
    }
}
