package com.cropcoders.procurement.enums;

/** Entries in a booking's timeline (what the farmer sees under "Status history"). */
public enum BookingEventType {
    BOOKED,
    CANCELLED,
    CHECKED_IN,
    CALLED,
    SKIPPED,        // not at the counter when called - sent back to the queue
    COMPLETED,
    REJECTED,
    NO_SHOW,
    PAYMENT_PENDING,
    PAID
}
