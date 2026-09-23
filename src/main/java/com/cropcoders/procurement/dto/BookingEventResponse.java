package com.cropcoders.procurement.dto;

import java.time.LocalDateTime;

import com.cropcoders.procurement.entity.BookingEvent;
import com.cropcoders.procurement.enums.BookingEventType;

public record BookingEventResponse(BookingEventType event, String note, LocalDateTime at) {

    public static BookingEventResponse from(BookingEvent event) {
        return new BookingEventResponse(event.getType(), event.getNote(), event.getCreatedAt());
    }
}
