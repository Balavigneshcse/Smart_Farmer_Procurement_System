package com.cropcoders.procurement.dto;

import java.util.List;

/** The latest notifications, newest first, and how many are not read yet. */
public record NotificationListResponse(List<NotificationResponse> items, long unread) {
}
