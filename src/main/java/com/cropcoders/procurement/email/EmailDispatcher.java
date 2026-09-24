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
        Long id = event.notificationId();
        long start = System.currentTimeMillis();
        try {
            String ref = emailSender.send(event.message());
            log.info("Email \"{}\" sent to {} in {} ms", event.message().subject().replaceAll("\\d{6}", "******"),
                    masked(event.message().to()), System.currentTimeMillis() - start);
            if (id != null) {
                notificationRepository.updateEmailStatus(id, EmailStatus.SENT, shorten(ref));
            }
        } catch (RuntimeException e) {
            if (id == null) {
                // OTP email: the farmer can press "Resend" after a minute.
                log.warn("OTP email could not be sent: {}", e.getMessage());
                return;
            }
            log.warn("Email for notification {} failed: {}", id, e.getMessage());
            notificationRepository.updateEmailStatus(id, EmailStatus.FAILED, shorten(e.getMessage()));
        }
    }

    /** "ravi.kumar@gmail.com" -> "r***@gmail.com", so the log does not show full addresses. */
    private static String masked(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }

    private static String shorten(String value) {
        return value == null || value.length() <= MAX_REF_LENGTH ? value : value.substring(0, MAX_REF_LENGTH);
    }
}
