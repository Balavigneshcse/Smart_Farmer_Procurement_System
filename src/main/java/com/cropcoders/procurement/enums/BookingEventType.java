package com.cropcoders.procurement.enums;

/** Entries in a booking's timeline (what the farmer sees under "Status history"). */
public enum BookingEventType {
    BOOKED,
    CANCELLED,
    CHECKED_IN,
    CALLED,
    COMPLETED,
    REJECTED,
    NO_SHOW,
    PAYMENT_PENDING,
    PAID
}
