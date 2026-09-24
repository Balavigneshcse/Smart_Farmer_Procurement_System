package com.cropcoders.procurement.email;

/**
 * An email to send once the current transaction commits.
 * notificationId: the notification whose email status is updated afterwards; null for OTP emails
 * (those are not stored, because they contain the code).
 */
public record EmailQueuedEvent(Long notificationId, EmailMessage message) {
}
