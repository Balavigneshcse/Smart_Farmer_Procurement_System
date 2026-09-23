package com.cropcoders.procurement.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development SMS sender: prints the message in the IntelliJ console instead of sending it. */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSmsSender.class);

    @Override
    public String send(String mobile, String message) {
        log.info("""

                ======== SMS to +91 {} ========
                {}
                ===========================================""", mobile, message);
        return "console";
    }
}
