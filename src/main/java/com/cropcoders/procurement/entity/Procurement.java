package com.cropcoders.procurement.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** The weighment and quality check of one booking at the counter. */
@Entity
@Table(name = "procurements")
public class Procurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    private BigDecimal weightQtl;
    private BigDecimal moisturePct;

    /** A, B or C */
    private String grade;

    /** The crop rate on the day, kept here so later rate changes don't change old amounts. */
    private BigDecimal ratePerQtl;

    /** weight x rate if accepted, 0 if rejected */
    private BigDecimal amount;

    private boolean accepted;
    private String remarks;

    /** User id of the operator who recorded it. */
    private Long recordedBy;

    private LocalDateTime recordedAt;

    protected Procurement() {
        // for JPA
    }

    public Procurement(Booking booking, BigDecimal weightQtl, BigDecimal moisturePct, String grade,
                       BigDecimal ratePerQtl, BigDecimal amount, boolean accepted, String remarks, Long recordedBy) {
        this.booking = booking;
        this.weightQtl = weightQtl;
        this.moisturePct = moisturePct;
        this.grade = grade;
        this.ratePerQtl = ratePerQtl;
        this.amount = amount;
        this.accepted = accepted;
        this.remarks = remarks;
        this.recordedBy = recordedBy;
    }

    @PrePersist
    void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public BigDecimal getWeightQtl() {
        return weightQtl;
    }

    public BigDecimal getMoisturePct() {
        return moisturePct;
    }

    public String getGrade() {
        return grade;
    }

    public BigDecimal getRatePerQtl() {
        return ratePerQtl;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getRemarks() {
        return remarks;
    }

    public Long getRecordedBy() {
        return recordedBy;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
