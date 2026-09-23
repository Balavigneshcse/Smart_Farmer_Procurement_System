package com.cropcoders.procurement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.dto.PaymentListResponse;
import com.cropcoders.procurement.dto.PaymentOrderResponse;
import com.cropcoders.procurement.dto.PaymentView;
import com.cropcoders.procurement.dto.VerifyPaymentRequest;
import com.cropcoders.procurement.entity.Booking;
import com.cropcoders.procurement.entity.BookingEvent;
import com.cropcoders.procurement.entity.Payment;
import com.cropcoders.procurement.enums.BookingEventType;
import com.cropcoders.procurement.enums.PaymentStatus;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.payment.RazorpayGateway;
import com.cropcoders.procurement.repository.BookingEventRepository;
import com.cropcoders.procurement.repository.PaymentRepository;
import com.cropcoders.procurement.security.AuthUser;

/**
 * Paying farmers (admin). In the demo the admin pays through Razorpay Checkout in test mode;
 * in real life this step would be a payout to the farmer's bank account (e.g. RazorpayX / DBT).
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final BookingEventRepository eventRepository;
    private final RazorpayGateway razorpay;

    public PaymentService(PaymentRepository paymentRepository, BookingEventRepository eventRepository,
                          RazorpayGateway razorpay) {
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.razorpay = razorpay;
    }

    @Transactional(readOnly = true)
    public PaymentListResponse list(PaymentStatus status) {
        List<Payment> all = paymentRepository.findAllForAdmin();
        LocalDate today = LocalDate.now();

        List<Payment> pending = all.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING).toList();
        List<Payment> paid = all.stream().filter(p -> p.getStatus() == PaymentStatus.PAID).toList();
        List<Payment> paidToday = paid.stream()
                .filter(p -> p.getPaidAt() != null && p.getPaidAt().toLocalDate().equals(today))
                .toList();

        var summary = new PaymentListResponse.Summary(pending.size(), sum(pending), paidToday.size(), sum(paidToday),
                sum(paid));
        List<PaymentView> rows = all.stream()
                .filter(p -> status == null || p.getStatus() == status)
                .map(PaymentView::from)
                .toList();
        return new PaymentListResponse(rows, summary, razorpay.isConfigured());
    }

    /**
     * Step 1: create (or reuse) the Razorpay order for this payment.
     * The amount always comes from our database, never from the browser.
     */
    @Transactional
    public PaymentOrderResponse createOrder(Long paymentId) {
        Payment payment = paymentRepository.findDetailedById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found."));
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PAID", "This payment is already done.");
        }
        Booking booking = payment.getProcurement().getBooking();

        if (payment.getRzpOrderId() == null) {
            String orderId = razorpay.createOrder(payment.getAmountPaise(), "PAY-" + payment.getId(),
                    Map.of("token", booking.getToken(), "farmer", booking.getFarmer().getName()));
            payment.setRzpOrderId(orderId);
        }
        return new PaymentOrderResponse(
                payment.getId(),
                razorpay.keyId(),
                payment.getRzpOrderId(),
                payment.getAmountPaise(),
                "INR",
                "Procurement " + booking.getToken() + " - " + booking.getCrop().getNameEn(),
                booking.getFarmer().getName(),
                booking.getFarmer().getMobile());
    }

    /** Step 2: after Checkout succeeds, check Razorpay's signature and mark the payment as paid. */
    @Transactional
    public PaymentView verify(VerifyPaymentRequest request, AuthUser admin) {
        Payment payment = paymentRepository.findDetailedByRzpOrderId(request.razorpayOrderId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND",
                        "No payment found for this Razorpay order."));

        if (!razorpay.isValidSignature(request.razorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature())) {
            log.warn("Invalid Razorpay signature for order {}", request.razorpayOrderId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "SIGNATURE_INVALID",
                    "Payment could not be verified. It has not been marked as paid.");
        }

        if (payment.getStatus() != PaymentStatus.PAID) {       // verifying twice is harmless
            payment.markPaid(request.razorpayPaymentId());
            eventRepository.save(new BookingEvent(payment.getProcurement().getBooking(), BookingEventType.PAID,
                    "Rs " + payment.amountRupees() + ", ref " + request.razorpayPaymentId(), admin.id()));
        }
        return PaymentView.from(payment);
    }

    private static BigDecimal sum(List<Payment> payments) {
        return BigDecimal.valueOf(payments.stream().mapToLong(Payment::getAmountPaise).sum(), 2);
    }
}
