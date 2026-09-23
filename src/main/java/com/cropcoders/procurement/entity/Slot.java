package com.cropcoders.procurement.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A bookable time window at a centre, e.g. Thanjavur, 24 Sep, 09:00-09:30, 9 places.
 * Rows are created by SlotService; "booked" is only changed with atomic UPDATE queries.
 */
@Entity
@Table(name = "slots")
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "centre_id")
    private Centre centre;

    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private int capacity;
    private int booked;

    protected Slot() {
        // for JPA
    }

    public int available() {
        return Math.max(0, capacity - booked);
    }

    public LocalDateTime startsAt() {
        return slotDate.atTime(startTime);
    }

    public LocalDateTime endsAt() {
        return slotDate.atTime(endTime);
    }

    public boolean hasEnded(LocalDateTime now) {
        return !endsAt().isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Centre getCentre() {
        return centre;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getBooked() {
        return booked;
    }
}
