package com.cropcoders.procurement;

import com.cropcoders.procurement.config.AppProperties;

/** The app settings used by unit tests (same values as application.yml, no real keys). */
public final class TestProperties {

    private TestProperties() {
    }

    public static AppProperties withJwtSecret(String secret) {
        return new AppProperties("test",
                new AppProperties.Jwt(secret, 168, 12, 15),
                new AppProperties.Otp(5, 3, 60, 5),
                new AppProperties.Sms("console"),
                new AppProperties.Booking(7),
                new AppProperties.Queue(15),
                new AppProperties.Razorpay("", "", "https://api.razorpay.com/v1"));
    }
}
