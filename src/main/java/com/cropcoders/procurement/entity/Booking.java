package com.cropcoders.procurement.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicUpdate;

import com.cropcoders.procurement.enums.BookingStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One farmer visit: a crop brought to a centre in a booked slot.
 *
 * {@code @DynamicUpdate}: when a booking is saved, Hibernate writes only the columns that were changed.
 * Without it every column is written back, so changing one field could also undo a status that another
 * request saved in the meantime (e.g. turn a just-cancelled booking back into BOOKED).
 */
@Entity
@Table(name = "bookings")
@DynamicUpdate
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Shown to the farmer and on the TV, e.g. TNJ-017. Unique per centre per day. */
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farmer_id")
    private User farmer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private Slot slot;

    // Copied from the slot so queue queries don't need a join.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "centre_id")
    private Centre centre;

    private LocalDate bookingDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crop_id")
    private Crop crop;

    private BigDecimal estQtyQtl;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    // Filled in by the queue (M4)
    private LocalDateTime queuePriority;
    private LocalDateTime checkedInAt;
    private LocalDateTime calledAt;
    private Integer counterNo;
    private LocalDateTime completedAt;

    // Set by the automation jobs (M6)
    private boolean reminderSent;
    private boolean turnAlertSent;

    private LocalDateTime createdAt;

    protected Booking() {
        // for JPA
    }

    public Booking(String token, User farmer, Slot slot, Crop crop, BigDecimal estQtyQtl) {
        this.token = token;
        this.farmer = farmer;
        this.slot = slot;
        this.centre = slot.getCentre();
        this.bookingDate = slot.getSlotDate();
        this.crop = crop;
        this.estQtyQtl = estQtyQtl;
        this.status = BookingStatus.BOOKED;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** The farmer may cancel until their slot starts, and only before checking in. */
    public boolean isCancellable(LocalDateTime now) {
        return status == BookingStatus.BOOKED && now.isBefore(slot.startsAt());
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public User getFarmer() {
        return farmer;
    }

    public Slot getSlot() {
        return slot;
    }

    public Centre getCentre() {
        return centre;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public Crop getCrop() {
        return crop;
    }

    public BigDecimal getEstQtyQtl() {
        return estQtyQtl;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public LocalDateTime getQueuePriority() {
        return queuePriority;
    }

    public void setQueuePriority(LocalDateTime queuePriority) {
        this.queuePriority = queuePriority;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public LocalDateTime getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(LocalDateTime calledAt) {
        this.calledAt = calledAt;
    }

    public Integer getCounterNo() {
        return counterNo;
    }

    public void setCounterNo(Integer counterNo) {
        this.counterNo = counterNo;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isReminderSent() {
        return reminderSent;
    }

    public void setReminderSent(boolean reminderSent) {
        this.reminderSent = reminderSent;
    }

    public boolean isTurnAlertSent() {
        return turnAlertSent;
    }

    public void setTurnAlertSent(boolean turnAlertSent) {
        this.turnAlertSent = turnAlertSent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
