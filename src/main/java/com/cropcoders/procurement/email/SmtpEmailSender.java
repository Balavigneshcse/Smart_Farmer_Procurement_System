package com.cropcoders.procurement.email;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.cropcoders.procurement.config.AppProperties;

import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends real emails through Gmail SMTP (spring.mail.* in application.yml).
 * Needs MAIL_USERNAME and MAIL_PASSWORD (a Gmail App Password, not the normal password) in .env.
 *
 * Opening a Gmail connection (connect + TLS + login) takes about 2-3 seconds, so one connection is
 * kept open and reused for the next emails, like a database connection pool with one connection.
 * If Gmail has closed it in the meantime, the sender reconnects and tries once more.
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);
    private static final long MAX_IDLE_MS = 4 * 60_000;   // Gmail closes connections that stay idle for long

    private final JavaMailSenderImpl mailSender;
    private final InternetAddress from;
    private Transport transport;   // the kept-open connection; used by one email at a time (synchronized)
    private long lastUsed;

    public SmtpEmailSender(JavaMailSenderImpl mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        AppProperties.Email config = properties.email();
        if (config.from() == null || config.from().isBlank()) {
            throw new IllegalStateException(
                    "EMAIL_PROVIDER=smtp needs MAIL_USERNAME and MAIL_PASSWORD (Gmail App Password) in .env");
        }
        try {
            this.from = new InternetAddress(config.from(), config.fromName(), StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Opens the connection in the background right after startup, so the first OTP is fast
     * and a wrong App Password shows up in the log at once.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpInBackground() {
        Thread.ofVirtual().name("gmail-warm-up").start(this::warmUp);
    }

    private void warmUp() {
        long start = System.currentTimeMillis();
        try {
            synchronized (this) {
                connection();
            }
            log.info("Gmail SMTP ready ({} ms)", System.currentTimeMillis() - start);
        } catch (MessagingException e) {
            log.error("Could not log in to Gmail SMTP - check MAIL_USERNAME and MAIL_PASSWORD (App Password) in .env: {}",
                    e.getMessage());
        }
    }

    @Override
    public synchronized String send(EmailMessage message) {
        MimeMessage mime;
        try {
            mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            if (message.qrPng() != null) {
                // Sent inside the email itself, so it shows even when the mail app blocks pictures from the web.
                helper.addInline(EmailMessage.QR_CONTENT_ID, new ByteArrayResource(message.qrPng()), "image/png");
            }
            mime.saveChanges();
        } catch (MessagingException e) {
            throw new EmailSendException("Could not build email: " + e.getMessage(), e);
        }

        try {
            return sendOn(connection(), mime);
        } catch (MessagingException firstTry) {
            // Most likely Gmail closed the idle connection: open a new one and try once more.
            disconnect();
            try {
                return sendOn(connection(), mime);
            } catch (MessagingException e) {
                disconnect();
                throw new EmailSendException("Could not send email: " + e.getMessage(), e);
            }
        }
    }

    /** The kept-open connection; a new one if there is none or it has been idle so long that Gmail may have dropped it. */
    private Transport connection() throws MessagingException {
        long now = System.currentTimeMillis();
        if (transport != null && now - lastUsed > MAX_IDLE_MS) {
            disconnect();
        }
        if (transport == null) {
            Transport t = mailSender.getSession().getTransport("smtp");
            t.connect(mailSender.getHost(), mailSender.getPort(), mailSender.getUsername(), mailSender.getPassword());
            transport = t;
        }
        lastUsed = now;
        return transport;
    }

    private static String sendOn(Transport t, MimeMessage mime) throws MessagingException {
        t.sendMessage(mime, mime.getAllRecipients());
        return mime.getMessageID();
    }

    @PreDestroy
    public synchronized void disconnect() {
        if (transport != null) {
            try {
                transport.close();
            } catch (MessagingException e) {
                // already closed
            }
            transport = null;
        }
    }
}
