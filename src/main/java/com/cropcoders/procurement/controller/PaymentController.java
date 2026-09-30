package com.cropcoders.procurement.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cropcoders.procurement.dto.PaymentListResponse;
import com.cropcoders.procurement.dto.PaymentOrderResponse;
import com.cropcoders.procurement.dto.PaymentView;
import com.cropcoders.procurement.dto.VerifyPaymentRequest;
import com.cropcoders.procurement.enums.PaymentStatus;
import com.cropcoders.procurement.security.AuthUser;
import com.cropcoders.procurement.service.PaymentService;

import jakarta.validation.Valid;

/** Farmer payments. A centre admin pays the farmers of their own centre; the super admin sees every centre. */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** status = PENDING, PAID or FAILED; leave it out for all payments. */
    @GetMapping("/api/admin/payments")
    public PaymentListResponse list(@AuthenticationPrincipal AuthUser admin,
                                    @RequestParam(name = "status", required = false) PaymentStatus status) {
        return paymentService.list(status, admin);
    }

    @PostMapping("/api/payments/{id}/order")
    public PaymentOrderResponse createOrder(@AuthenticationPrincipal AuthUser admin, @PathVariable("id") Long paymentId) {
        return paymentService.createOrder(paymentId, admin);
    }

    @PostMapping("/api/payments/verify")
    public PaymentView verify(@AuthenticationPrincipal AuthUser admin, @Valid @RequestBody VerifyPaymentRequest request) {
        return paymentService.verify(request, admin);
    }
}
