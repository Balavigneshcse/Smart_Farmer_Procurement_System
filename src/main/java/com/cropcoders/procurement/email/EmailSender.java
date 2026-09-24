package com.cropcoders.procurement.email;

/**
 * Sends one email. Which implementation is used depends on app.email.provider:
 * "console" (prints to the log, for development) or "smtp" (Gmail with an App Password).
 */
public interface EmailSender {

    /**
     * @return a reference from the provider (message id), saved in the notification log
     * @throws EmailSendException if the email could not be sent
     */
    String send(EmailMessage message);
}
