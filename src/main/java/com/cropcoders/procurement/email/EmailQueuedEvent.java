package com.cropcoders.procurement.email;

/** A notification with an email was saved; the email is sent once that transaction commits. */
public record EmailQueuedEvent(Long notificationId, EmailMessage message) {
}
