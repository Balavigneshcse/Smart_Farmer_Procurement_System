package com.cropcoders.procurement.entity;

import java.time.LocalDateTime;

import com.cropcoders.procurement.enums.EmailStatus;
import com.cropcoders.procurement.enums.NotificationEvent;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A message to a farmer: shown on their dashboard and, for key events, also sent by email. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String email;
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    private NotificationEvent event;

    private String subject;
    private String message;

    @Enumerated(EnumType.STRING)
    private EmailStatus emailStatus;

    private String providerRef;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    protected Notification() {
        // for JPA
    }

    public Notification(Long userId, String email, Long bookingId, NotificationEvent event, String subject,
                        String message, EmailStatus emailStatus) {
        this.userId = userId;
        this.email = email;
        this.bookingId = bookingId;
        this.event = event;
        this.subject = subject;
        this.message = message;
        this.emailStatus = emailStatus;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public NotificationEvent getEvent() {
        return event;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public EmailStatus getEmailStatus() {
        return emailStatus;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }
}
