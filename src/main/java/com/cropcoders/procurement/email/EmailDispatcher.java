package com.cropcoders.procurement.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cropcoders.procurement.enums.EmailStatus;
import com.cropcoders.procurement.repository.NotificationRepository;

/**
 * Sends notification emails in the background.
 *
 * AFTER_COMMIT: the email goes out only if the booking change was really saved.
 * {@code @Async}: sending takes 1-3 seconds, so it runs on another thread and the
 * operator's or farmer's click is answered straight away.
 */
@Component
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);
    private static final int MAX_REF_LENGTH = 100;

    private final EmailSender emailSender;
    private final NotificationRepository notificationRepository;

    public EmailDispatcher(EmailSender emailSender, NotificationRepository notificationRepository) {
        this.emailSender = emailSender;
        this.notificationRepository = notificationRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailQueued(EmailQueuedEvent event) {
        try {
            String ref = emailSender.send(event.message());
            notificationRepository.updateEmailStatus(event.notificationId(), EmailStatus.SENT, shorten(ref));
        } catch (RuntimeException e) {
            log.warn("Email for notification {} failed: {}", event.notificationId(), e.getMessage());
            notificationRepository.updateEmailStatus(event.notificationId(), EmailStatus.FAILED, shorten(e.getMessage()));
        }
    }

    private static String shorten(String value) {
        return value == null || value.length() <= MAX_REF_LENGTH ? value : value.substring(0, MAX_REF_LENGTH);
    }
}
