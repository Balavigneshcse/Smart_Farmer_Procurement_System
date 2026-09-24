package com.cropcoders.procurement.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cropcoders.procurement.dto.NotificationListResponse;
import com.cropcoders.procurement.security.AuthUser;
import com.cropcoders.procurement.service.NotificationService;

/** The logged-in farmer's notifications (only farmers can call these, see SecurityConfig). */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/my")
    public NotificationListResponse myNotifications(@AuthenticationPrincipal AuthUser farmer) {
        return notificationService.myNotifications(farmer.id());
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthUser farmer) {
        notificationService.markAllRead(farmer.id());
    }
}
