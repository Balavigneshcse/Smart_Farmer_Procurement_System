package com.cropcoders.procurement.dto;

import java.time.LocalDateTime;

import com.cropcoders.procurement.entity.Notification;
import com.cropcoders.procurement.enums.EmailStatus;
import com.cropcoders.procurement.enums.NotificationEvent;

/** One notification on the farmer's dashboard (already in the farmer's language). */
public record NotificationResponse(
        Long id,
        NotificationEvent event,
        String subject,
        String message,
        Long bookingId,
        boolean emailed,
        LocalDateTime createdAt,
        boolean read) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getEvent(), n.getSubject(), n.getMessage(), n.getBookingId(),
                n.getEmailStatus() == EmailStatus.SENT,
                n.getCreatedAt(), n.getReadAt() != null);
    }
}
