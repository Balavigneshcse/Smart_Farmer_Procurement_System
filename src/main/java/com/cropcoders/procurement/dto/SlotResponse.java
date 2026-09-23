package com.cropcoders.procurement.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.cropcoders.procurement.entity.Slot;

/** bookable = not over yet and has a free place. */
public record SlotResponse(
        Long id,
        LocalTime startTime,
        LocalTime endTime,
        int capacity,
        int booked,
        int available,
        boolean past,
        boolean bookable) {

    public static SlotResponse from(Slot slot, LocalDateTime now) {
        boolean past = slot.hasEnded(now);
        return new SlotResponse(slot.getId(), slot.getStartTime(), slot.getEndTime(), slot.getCapacity(),
                slot.getBooked(), slot.available(), past, !past && slot.available() > 0);
    }
}
