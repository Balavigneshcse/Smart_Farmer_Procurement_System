package com.cropcoders.procurement.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.cropcoders.procurement.TestProperties;
import com.cropcoders.procurement.config.AppProperties;

class SecretGuardTest {

    private static final String DEV_SECRET = "local-dev-secret-change-me-on-render-0123456789";

    @Test
    void developmentSecretOnAServerStopsTheApp() {
        assertThatThrownBy(() -> SecretGuard.requireRealSecret(DEV_SECRET, "JWT_SECRET", "https://sfps.example.in"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        // http on a server address is not "local" either
        assertThatThrownBy(() -> SecretGuard.requireRealSecret(DEV_SECRET, "JWT_SECRET", "http://13.233.10.20"))
                .isInstanceOf(IllegalStateException.class);
        // a name that only starts like localhost is someone else's server
        assertThatThrownBy(() -> SecretGuard.requireRealSecret(DEV_SECRET, "JWT_SECRET", "http://localhost.example.com"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void developmentSecretIsFineOnThisPc() {
        assertThatCode(() -> SecretGuard.requireRealSecret(DEV_SECRET, "JWT_SECRET", "http://localhost:8080"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SecretGuard.requireRealSecret(DEV_SECRET, "JWT_SECRET", "http://127.0.0.1:8080"))
                .doesNotThrowAnyException();
    }

    @Test
    void realSecretIsFineAnywhere() {
        assertThatCode(() -> SecretGuard.requireRealSecret("f3K9xQ2mVb7LzP0rT8wYc4Nd6Hs1Ja5E", "JWT_SECRET",
                "https://sfps.example.in")).doesNotThrowAnyException();
    }

    @Test
    void jwtServiceUsesTheGuard() {
        AppProperties base = TestProperties.withJwtSecret(DEV_SECRET);
        AppProperties onServer = new AppProperties(base.name(), base.jwt(), base.otp(), base.staffLogin(),
                new AppProperties.Email("console", "noreply@example.com", "Smart Farmer Procurement System", 250,
                        "https://sfps.example.in"),
                base.booking(), base.queue(), base.razorpay());

        assertThatCode(() -> new JwtService(base)).doesNotThrowAnyException();       // localhost
        assertThatThrownBy(() -> new JwtService(onServer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
