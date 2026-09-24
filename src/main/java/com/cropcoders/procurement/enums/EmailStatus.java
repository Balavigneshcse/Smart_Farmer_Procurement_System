package com.cropcoders.procurement.enums;

/** What happened to the email of a notification. */
public enum EmailStatus {
    PENDING,        // saved; being sent in the background
    SENT,
    FAILED,
    SKIPPED,        // today's email limit was reached - shown in the app only
    IN_APP_ONLY     // this kind of notification never sends an email
}
