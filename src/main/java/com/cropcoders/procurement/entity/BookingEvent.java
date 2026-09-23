package com.cropcoders.procurement.entity;

import java.time.LocalDateTime;

import com.cropcoders.procurement.enums.BookingEventType;

import jakarta.persistence.Column;
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

/** One line in a booking's timeline, e.g. "Checked in at 09:05". */
@Entity
@Table(name = "booking_events")
public class BookingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "event")
    private BookingEventType type;

    /** Extra detail for staff (English). */
    private String note;

    /** User id of whoever caused the event (farmer, operator or admin); null for automatic events. */
    private Long createdBy;

    private LocalDateTime createdAt;

    protected BookingEvent() {
        // for JPA
    }

    public BookingEvent(Booking booking, BookingEventType type, String note, Long createdBy) {
        this.booking = booking;
        this.type = type;
        this.note = note;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public BookingEventType getType() {
        return type;
    }

    public String getNote() {
        return note;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
