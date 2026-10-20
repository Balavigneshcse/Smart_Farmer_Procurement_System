package com.cropcoders.procurement.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.enums.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Creates and checks signed tokens (JWT).
 *
 * Login token: proves who the user is. Sent by the browser as "Authorization: Bearer <token>".
 * Registration token: short-lived proof that a new farmer verified their email with OTP;
 * it can only be used to finish registration, never to call other APIs.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_CENTRE = "centreId";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String PURPOSE_REGISTER = "register";

    private final SecretKey key;
    private final AppProperties.Jwt config;

    public JwtService(AppProperties properties) {
        this.config = properties.jwt();
        // Also protects QrLinkSigner, which signs the QR links with the same secret.
        SecretGuard.requireRealSecret(config.secret(), "JWT_SECRET", properties.email().appUrl());
        byte[] secret = config.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 characters long");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String createLoginToken(AuthUser user) {
        long hours = user.role() == Role.FARMER ? config.farmerExpiryHours() : config.staffExpiryHours();
        JwtBuilder builder = baseToken(user.id().toString(), Duration.ofHours(hours))
                .claim(CLAIM_ROLE, user.role().name())
                .claim(CLAIM_NAME, user.name());
        if (user.centreId() != null) {
            builder.claim(CLAIM_CENTRE, user.centreId());
        }
        return builder.compact();
    }

    /** Returns the user for a valid login token, or empty if it is invalid, expired or not a login token. */
    public Optional<AuthUser> parseLoginToken(String token) {
        return parse(token)
                .filter(claims -> claims.get(CLAIM_PURPOSE) == null)
                .map(claims -> {
                    Number centreId = claims.get(CLAIM_CENTRE, Number.class);
                    return new AuthUser(
                            Long.valueOf(claims.getSubject()),
                            Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                            claims.get(CLAIM_NAME, String.class),
                            centreId == null ? null : centreId.longValue());
                });
    }

    public String createRegistrationToken(String email) {
        return baseToken(email, Duration.ofMinutes(config.registrationExpiryMinutes()))
                .claim(CLAIM_PURPOSE, PURPOSE_REGISTER)
                .compact();
    }

    /** Returns the verified email address for a valid registration token. */
    public Optional<String> parseRegistrationToken(String token) {
        return parse(token)
                .filter(claims -> PURPOSE_REGISTER.equals(claims.get(CLAIM_PURPOSE)))
                .map(Claims::getSubject);
    }

    private JwtBuilder baseToken(String subject, Duration validFor) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validFor)))
                .signWith(key);
    }

    private Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
