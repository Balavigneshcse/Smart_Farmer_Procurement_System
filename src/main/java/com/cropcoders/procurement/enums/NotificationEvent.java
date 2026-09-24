package com.cropcoders.procurement.enums;

/**
 * Things a farmer is told about. Every notification is shown in the app;
 * sendsEmail = true also sends an email (kept to key events, to stay inside the free email quota).
 * key = the text key in messages*.properties (notify.KEY.subject / notify.KEY.text).
 */
public enum NotificationEvent {
    BOOKING_CONFIRMED("booking-confirmed", true),
    BOOKING_CANCELLED("booking-cancelled", true),
    REMINDER("reminder", true),
    CHECKED_IN("checked-in", false),     // the farmer is standing at the desk
    TURN_NEAR("turn-near", true),
    CALLED("called", false),             // the TV and the live page already show it
    PROCURED("procured", true),
    REJECTED("rejected", true),
    PAID("paid", true),
    NO_SHOW("no-show", true);

    private final String key;
    private final boolean sendsEmail;

    NotificationEvent(String key, boolean sendsEmail) {
        this.key = key;
        this.sendsEmail = sendsEmail;
    }

    public String key() {
        return key;
    }

    public boolean sendsEmail() {
        return sendsEmail;
    }
}
