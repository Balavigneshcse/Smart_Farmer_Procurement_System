package com.cropcoders.procurement.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development email sender: prints the email in the IntelliJ console instead of sending it. */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

    @Override
    public String send(EmailMessage message) {
        log.info("""

                ======== Email to {} ========
                Subject: {}

                {}
                ===========================================""", message.to(), message.subject(), message.text());
        return "console";
    }
}
