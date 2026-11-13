package com.cropcoders.procurement.enums;

/**
 * What the app (this software) should improve, chosen by the farmer in the visit review (in this fixed order).
 * A code that is not listed here is refused (400), because the request cannot be read.
 */
public enum AppIssue {
    BOOKING,      // booking a slot
    LIVE_QUEUE,   // live queue and TV screen
    MESSAGES,     // emails and reminders
    LANGUAGE,     // language and easy reading
    LOGIN         // login with OTP
}
