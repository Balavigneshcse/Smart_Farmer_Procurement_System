package com.cropcoders.procurement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under "app:" in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String name, Jwt jwt, Otp otp, StaffLogin staffLogin, Email email, Booking booking,
                            Queue queue, Razorpay razorpay) {

    /**
     * Razorpay test-mode keys (from .env). testChargeLimitPaise: the most one test payment charges through
     * Razorpay (test accounts refuse large amounts; UPI allows at most Rs 1,00,000). A bigger farmer
     * payment is charged as this stand-in amount and recorded as paid in full.
     */
    public record Razorpay(String keyId, String keySecret, String apiUrl, long testChargeLimitPaise) {
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

    /** Staff passwords: after maxAttempts wrong passwords in a row the account is locked for lockMinutes. */
    public record StaffLogin(int maxAttempts, int lockMinutes) {
    }

    /**
     * provider: "console" prints emails in the log, "smtp" sends them with Gmail.
     * dailyLimit: notification emails per day (OTP emails are not counted, a farmer must always be able to log in).
     * appUrl: the website address, used for the links inside emails.
     */
    public record Email(String provider, String from, String fromName, int dailyLimit, String appUrl) {
    }
}
