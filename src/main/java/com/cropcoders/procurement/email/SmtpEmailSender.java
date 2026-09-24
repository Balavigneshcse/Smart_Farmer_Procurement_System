package com.cropcoders.procurement.email;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.cropcoders.procurement.config.AppProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends real emails through Gmail SMTP (spring.mail.* in application.yml).
 * Needs MAIL_USERNAME and MAIL_PASSWORD (a Gmail App Password, not the normal password) in .env.
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final InternetAddress from;

    public SmtpEmailSender(JavaMailSender mailSender, AppProperties properties) {
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

    @Override
    public String send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mime);
            return mime.getMessageID();
        } catch (MessagingException | MailException e) {
            throw new EmailSendException("Could not send email: " + e.getMessage(), e);
        }
    }
}
