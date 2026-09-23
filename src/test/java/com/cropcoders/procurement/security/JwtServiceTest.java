package com.cropcoders.procurement.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.enums.Role;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-0123456789";

    private final JwtService jwtService = new JwtService(properties(SECRET));

    @Test
    void loginTokenRoundTrip() {
        AuthUser operator = new AuthUser(7L, Role.OPERATOR, "Thanjavur Operator", 1L);

        Optional<AuthUser> parsed = jwtService.parseLoginToken(jwtService.createLoginToken(operator));

        assertThat(parsed).contains(operator);
    }

    @Test
    void farmerTokenHasNoCentre() {
        AuthUser farmer = new AuthUser(42L, Role.FARMER, "Murugan", null);

        assertThat(jwtService.parseLoginToken(jwtService.createLoginToken(farmer))).contains(farmer);
    }

    @Test
    void registrationTokenCannotBeUsedToLogIn() {
        String registrationToken = jwtService.createRegistrationToken("9876543210");

        assertThat(jwtService.parseLoginToken(registrationToken)).isEmpty();
        assertThat(jwtService.parseRegistrationToken(registrationToken)).contains("9876543210");
    }

    @Test
    void loginTokenCannotBeUsedToRegister() {
        String loginToken = jwtService.createLoginToken(new AuthUser(1L, Role.FARMER, "Murugan", null));

        assertThat(jwtService.parseRegistrationToken(loginToken)).isEmpty();
    }

    @Test
    void tamperedOrForeignTokensAreRejected() {
        String token = jwtService.createLoginToken(new AuthUser(1L, Role.FARMER, "Murugan", null));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        JwtService otherServer = new JwtService(properties("a-completely-different-secret-9876543210"));

        assertThat(jwtService.parseLoginToken(tampered)).isEmpty();
        assertThat(otherServer.parseLoginToken(token)).isEmpty();
        assertThat(jwtService.parseLoginToken("not-a-jwt")).isEmpty();
    }

    private static AppProperties properties(String secret) {
        return new AppProperties("test",
                new AppProperties.Jwt(secret, 168, 12, 15),
                new AppProperties.Otp(5, 3, 60, 5),
                new AppProperties.Sms("console"),
                new AppProperties.Booking(7));
    }
}
