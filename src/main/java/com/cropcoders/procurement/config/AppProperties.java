package com.cropcoders.procurement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under "app:" in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String name, Jwt jwt, Otp otp, Email email, Booking booking, Queue queue,
                            Razorpay razorpay) {

    /** Razorpay test-mode keys (from .env locally, environment variables on Render). */
    public record Razorpay(String keyId, String keySecret, String apiUrl) {
    }

    /**
     * lateGraceMinutes: a farmer who checks in later than slot end + this joins the back of the queue.
     * noShowAfterMinutes: a booking not checked in by slot end + this is marked as missed (NO_SHOW).
     */
    public record Queue(int lateGraceMinutes, int noShowAfterMinutes) {
    }

    /**
     * daysAhead: how many days ahead (including today) farmers can book.
     * reminderBeforeMinutes: the reminder email goes out this long before the slot starts.
     */
    public record Booking(int daysAhead, int reminderBeforeMinutes) {
    }

    /** Login tokens. The secret must be at least 32 characters. */
    public record Jwt(String secret, int farmerExpiryHours, int staffExpiryHours, int registrationExpiryMinutes) {
    }

    /** OTP rules that protect both security and the free email quota. */
    public record Otp(int validMinutes, int maxAttempts, int resendSeconds, int maxPerHour) {
    }

    /**
     * provider: "console" prints emails in the log, "smtp" sends them with Gmail.
     * dailyLimit: notification emails per day (OTP emails are not counted, a farmer must always be able to log in).
     * appUrl: the website address, used for the links inside emails.
     */
    public record Email(String provider, String from, String fromName, int dailyLimit, String appUrl) {
    }
}
