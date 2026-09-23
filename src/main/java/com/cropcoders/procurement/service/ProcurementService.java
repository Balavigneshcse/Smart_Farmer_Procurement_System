package com.cropcoders.procurement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.dto.OperatorActionResponse;
import com.cropcoders.procurement.dto.WeighmentRequest;
import com.cropcoders.procurement.entity.Booking;
import com.cropcoders.procurement.entity.BookingEvent;
import com.cropcoders.procurement.entity.Centre;
import com.cropcoders.procurement.entity.Crop;
import com.cropcoders.procurement.entity.Payment;
import com.cropcoders.procurement.entity.Procurement;
import com.cropcoders.procurement.enums.BookingEventType;
import com.cropcoders.procurement.enums.BookingStatus;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.repository.BookingEventRepository;
import com.cropcoders.procurement.repository.PaymentRepository;
import com.cropcoders.procurement.repository.ProcurementRepository;
import com.cropcoders.procurement.security.AuthUser;

/**
 * Weighment at the counter: weight, moisture and grade decide whether the crop is accepted
 * and how much the farmer is paid. Accepting creates a PENDING payment for the admin.
 */
@Service
public class ProcurementService {

    private final QueueService queueService;
    private final ProcurementRepository procurementRepository;
    private final PaymentRepository paymentRepository;
    private final BookingEventRepository eventRepository;

    public ProcurementService(QueueService queueService, ProcurementRepository procurementRepository,
                              PaymentRepository paymentRepository, BookingEventRepository eventRepository) {
        this.queueService = queueService;
        this.procurementRepository = procurementRepository;
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public OperatorActionResponse recordWeighment(AuthUser operator, Long bookingId, WeighmentRequest request) {
        Centre centre = queueService.operatorCentre(operator);
        Booking booking = queueService.bookingAtCounter(centre, bookingId);
        Crop crop = booking.getCrop();

        boolean accepted = request.accepted();
        String remarks = request.remarks() == null ? null : request.remarks().trim();
        if (accepted && request.moisturePct().compareTo(crop.getMaxMoisturePct()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MOISTURE_TOO_HIGH",
                    "Moisture " + request.moisturePct() + "% is above the " + crop.getMaxMoisturePct()
                            + "% limit for " + crop.getNameEn() + ". The crop must be rejected.");
        }
        if (!accepted && (remarks == null || remarks.isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REMARKS_REQUIRED",
                    "Write the reason for rejecting the crop.");
        }

        // Amount = weight x today's rate for the crop (0 when rejected).
        BigDecimal rate = crop.getRatePerQtl();
        BigDecimal amount = accepted
                ? request.weightQtl().multiply(rate).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);

        Procurement procurement = procurementRepository.save(new Procurement(booking, request.weightQtl(),
                request.moisturePct(), request.grade(), rate, amount, accepted, remarks, operator.id()));

        booking.setStatus(accepted ? BookingStatus.COMPLETED : BookingStatus.REJECTED);
        booking.setCompletedAt(LocalDateTime.now());
        String details = request.weightQtl() + " qtl, moisture " + request.moisturePct() + "%, grade " + request.grade();
        eventRepository.save(new BookingEvent(booking,
                accepted ? BookingEventType.COMPLETED : BookingEventType.REJECTED,
                accepted ? details + ", Rs " + amount : details + " - " + remarks,
                operator.id()));

        if (accepted) {
            long paise = amount.movePointRight(2).longValueExact();
            paymentRepository.save(new Payment(procurement, paise));
            eventRepository.save(new BookingEvent(booking, BookingEventType.PAYMENT_PENDING, "Rs " + amount, null));
        }

        String message = accepted
                ? booking.getToken() + " accepted: " + request.weightQtl() + " qtl = Rs " + amount + " (payment pending)"
                : booking.getToken() + " rejected: " + remarks;
        return queueService.afterChange(centre, booking, message);
    }
}
