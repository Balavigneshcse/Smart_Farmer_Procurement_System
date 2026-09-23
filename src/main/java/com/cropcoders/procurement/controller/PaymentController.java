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

/** Farmer payments. Only role ADMIN can call these (see SecurityConfig). */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** status = PENDING, PAID or FAILED; leave it out for all payments. */
    @GetMapping("/api/admin/payments")
    public PaymentListResponse list(@RequestParam(name = "status", required = false) PaymentStatus status) {
        return paymentService.list(status);
    }

    @PostMapping("/api/payments/{id}/order")
    public PaymentOrderResponse createOrder(@PathVariable("id") Long paymentId) {
        return paymentService.createOrder(paymentId);
    }

    @PostMapping("/api/payments/verify")
    public PaymentView verify(@AuthenticationPrincipal AuthUser admin, @Valid @RequestBody VerifyPaymentRequest request) {
        return paymentService.verify(request, admin);
    }
}
