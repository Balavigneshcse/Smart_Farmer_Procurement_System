package com.cropcoders.procurement.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cropcoders.procurement.enums.PaymentStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Money owed to a farmer for an accepted procurement.
 * Amounts are stored in paise (1 rupee = 100 paise) as whole numbers, like Razorpay does,
 * so there are never rounding errors.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "procurement_id")
    private Procurement procurement;

    private long amountPaise;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    /** Razorpay order created when the admin clicks Pay. */
    private String rzpOrderId;

    /** Razorpay payment id after a successful, verified payment (the reference shown to the farmer). */
    private String rzpPaymentId;

    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    protected Payment() {
        // for JPA
    }

    public Payment(Procurement procurement, long amountPaise) {
        this.procurement = procurement;
        this.amountPaise = amountPaise;
        this.status = PaymentStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public BigDecimal amountRupees() {
        return BigDecimal.valueOf(amountPaise, 2);
    }

    public void markPaid(String paymentId) {
        this.status = PaymentStatus.PAID;
        this.rzpPaymentId = paymentId;
        this.paidAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Procurement getProcurement() {
        return procurement;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getRzpOrderId() {
        return rzpOrderId;
    }

    public void setRzpOrderId(String rzpOrderId) {
        this.rzpOrderId = rzpOrderId;
    }

    public String getRzpPaymentId() {
        return rzpPaymentId;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
