package com.cropcoders.procurement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
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
import com.cropcoders.procurement.enums.NotificationEvent;
import com.cropcoders.procurement.enums.PaymentStatus;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.payment.RazorpayGateway;
import com.cropcoders.procurement.entity.FarmerProfile;
import com.cropcoders.procurement.repository.BookingEventRepository;
import com.cropcoders.procurement.repository.FarmerProfileRepository;
import com.cropcoders.procurement.repository.PaymentRepository;
import com.cropcoders.procurement.security.AccessScope;
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
    private final NotificationService notificationService;
    private final FarmerProfileRepository profileRepository;

    public PaymentService(PaymentRepository paymentRepository, BookingEventRepository eventRepository,
                          RazorpayGateway razorpay, NotificationService notificationService,
                          FarmerProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.razorpay = razorpay;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public PaymentListResponse list(PaymentStatus status, AuthUser admin) {
        Long centreId = AccessScope.centreOf(admin);     // null = every centre (super admin)
        List<Payment> all = paymentRepository.findAllForAdmin().stream()
                .filter(p -> centreId == null || centreOf(p).equals(centreId))
                .toList();
        LocalDate today = LocalDate.now();

        List<Payment> pending = all.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING).toList();
        List<Payment> paid = all.stream().filter(p -> p.getStatus() == PaymentStatus.PAID).toList();
        List<Payment> paidToday = paid.stream()
                .filter(p -> p.getPaidAt() != null && p.getPaidAt().toLocalDate().equals(today))
                .toList();

        var summary = new PaymentListResponse.Summary(pending.size(), sum(pending), paidToday.size(), sum(paidToday),
                sum(paid));
        List<Payment> shown = all.stream().filter(p -> status == null || p.getStatus() == status).toList();
        // Where each farmer wants to be paid, fetched in one query.
        Map<Long, String> payTo = new HashMap<>();
        profileRepository.findAllById(shown.stream().map(PaymentService::farmerId).distinct().toList())
                .forEach(profile -> payTo.put(profile.getUserId(), profile.payoutSummary()));
        List<PaymentView> rows = shown.stream().map(p -> PaymentView.from(p, payTo.get(farmerId(p)))).toList();
        return new PaymentListResponse(rows, summary, razorpay.isConfigured());
    }

    /**
     * Step 1: create (or reuse) the Razorpay order for this payment.
     * The amount always comes from our database, never from the browser.
     */
    @Transactional
    public PaymentOrderResponse createOrder(Long paymentId, AuthUser admin) {
        Payment payment = paymentRepository.findDetailedById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found."));
        AccessScope.requireCentre(admin, centreOf(payment));
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PAID", "This payment is already done.");
        }
        Booking booking = payment.getProcurement().getBooking();

        // Test accounts refuse big amounts, so a large payment is charged as a stand-in amount (test mode only).
        long chargePaise = Math.min(payment.getAmountPaise(), razorpay.testChargeLimitPaise());
        if (payment.getRzpOrderId() == null) {
            String orderId = razorpay.createOrder(chargePaise, "PAY-" + payment.getId(),
                    Map.of("token", booking.getToken(), "farmer", booking.getFarmer().getName(),
                            "full_amount_paise", String.valueOf(payment.getAmountPaise())));
            payment.setRzpOrderId(orderId);
        }
        return new PaymentOrderResponse(
                payment.getId(),
                razorpay.keyId(),
                payment.getRzpOrderId(),
                chargePaise,
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
        AccessScope.requireCentre(admin, centreOf(payment));

        if (!razorpay.isValidSignature(request.razorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature())) {
            log.warn("Invalid Razorpay signature for order {}", request.razorpayOrderId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "SIGNATURE_INVALID",
                    "Payment could not be verified. It has not been marked as paid.");
        }

        if (payment.getStatus() != PaymentStatus.PAID) {       // verifying twice is harmless
            payment.markPaid(request.razorpayPaymentId());
            Booking booking = payment.getProcurement().getBooking();
            eventRepository.save(new BookingEvent(booking, BookingEventType.PAID,
                    "Rs " + payment.amountRupees() + ", ref " + request.razorpayPaymentId(), admin.id()));
            notificationService.notifyBooking(booking, NotificationEvent.PAID,
                    NotificationService.money(payment.amountRupees()), request.razorpayPaymentId());
        }
        String payTo = profileRepository.findById(farmerId(payment)).map(FarmerProfile::payoutSummary).orElse(null);
        return PaymentView.from(payment, payTo);
    }

    private static Long farmerId(Payment payment) {
        return payment.getProcurement().getBooking().getFarmer().getId();
    }

    private static Long centreOf(Payment payment) {
        return payment.getProcurement().getBooking().getCentre().getId();
    }

    private static BigDecimal sum(List<Payment> payments) {
        return BigDecimal.valueOf(payments.stream().mapToLong(Payment::getAmountPaise).sum(), 2);
    }
}
