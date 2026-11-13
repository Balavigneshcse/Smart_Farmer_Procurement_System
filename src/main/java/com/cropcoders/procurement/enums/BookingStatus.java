package com.cropcoders.procurement.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * BOOKED -> CHECKED_IN -> CALLED -> COMPLETED / REJECTED
 * BOOKED -> CANCELLED (by the farmer) or NO_SHOW (farmer never came)
 */
public enum BookingStatus {
    BOOKED,
    CHECKED_IN,
    CALLED,
    COMPLETED,
    REJECTED,
    CANCELLED,
    NO_SHOW;

    /** Statuses that still hold a place: a farmer can have only one of these per day. */
    public static final Set<BookingStatus> ACTIVE = EnumSet.of(BOOKED, CHECKED_IN, CALLED);

    /** The crop was weighed at the counter (accepted or rejected): the farmer can now review the visit. */
    public boolean isWeighed() {
        return this == COMPLETED || this == REJECTED;
    }
}
