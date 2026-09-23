package com.cropcoders.procurement.sms;

/**
 * Sends one SMS. Which implementation is used depends on app.sms.provider:
 * "console" (prints to the log, for development) or "textbee" (real SMS, added in M6).
 */
public interface SmsSender {

    /**
     * @param mobile  10-digit Indian mobile number (without +91)
     * @param message text in the farmer's language
     * @return a reference from the provider, for the notification log
     */
    String send(String mobile, String message);
}
