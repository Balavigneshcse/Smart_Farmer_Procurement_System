package com.cropcoders.procurement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under "app:" in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String name, Jwt jwt, Otp otp, Sms sms, Booking booking, Queue queue, Razorpay razorpay) {

    /** Razorpay test-mode keys (from .env locally, environment variables on Render). */
    public record Razorpay(String keyId, String keySecret, String apiUrl) {
    }

    /** A farmer who checks in later than slot end + lateGraceMinutes joins the back of the queue. */
    public record Queue(int lateGraceMinutes) {
    }

    /** How many days ahead (including today) farmers can book. */
    public record Booking(int daysAhead) {
    }

    /** Login tokens. The secret must be at least 32 characters. */
    public record Jwt(String secret, int farmerExpiryHours, int staffExpiryHours, int registrationExpiryMinutes) {
    }

    /** OTP rules that protect both security and the free SMS quota. */
    public record Otp(int validMinutes, int maxAttempts, int resendSeconds, int maxPerHour) {
    }

    /** "console" prints SMS in the log; "textbee" sends real SMS (added in M6). */
    public record Sms(String provider) {
    }
}
