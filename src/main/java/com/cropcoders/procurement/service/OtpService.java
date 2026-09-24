package com.cropcoders.procurement.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.email.EmailSendException;
import com.cropcoders.procurement.email.EmailSender;
import com.cropcoders.procurement.email.EmailTemplates;
import com.cropcoders.procurement.entity.OtpCode;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.repository.OtpCodeRepository;

/**
 * Sends and checks one-time passwords (sent by email).
 *
 * Rules (from application.yml, app.otp):
 *  - a new OTP only after resend-seconds, and at most max-per-hour per email (protects the free email quota)
 *  - an OTP is valid for valid-minutes and allows max-attempts wrong tries
 *  - only a BCrypt hash of the code is stored, never the code itself
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository otpCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final AppProperties.Otp rules;

    public OtpService(OtpCodeRepository otpCodeRepository, PasswordEncoder passwordEncoder, EmailSender emailSender,
                      EmailTemplates templates, AppProperties properties) {
        this.otpCodeRepository = otpCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.templates = templates;
        this.rules = properties.otp();
    }

    /**
     * Creates a new OTP and emails it in the given language. Returns seconds until a resend is allowed.
     * The email is sent right away (not in the background): if it fails, the farmer is told and
     * the OTP is not saved, so they can try again at once.
     */
    @Transactional
    public int sendOtp(String email, String language) {
        LocalDateTime now = LocalDateTime.now();

        otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(email).ifPresent(last -> {
            long waited = Duration.between(last.getCreatedAt(), now).toSeconds();
            if (waited < rules.resendSeconds()) {
                long wait = rules.resendSeconds() - waited;
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN",
                        "Please wait " + wait + " seconds before asking for a new OTP.", Map.of("seconds", wait));
            }
        });
        if (otpCodeRepository.countByEmailAndCreatedAtAfter(email, now.minusHours(1)) >= rules.maxPerHour()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LIMIT",
                    "Too many OTP requests. Please try again after one hour.");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otpCodeRepository.save(new OtpCode(email, passwordEncoder.encode(code), now,
                now.plusMinutes(rules.validMinutes())));

        try {
            emailSender.send(templates.otp(email, language, code, rules.validMinutes()));
        } catch (EmailSendException e) {
            log.warn("OTP email could not be sent: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_FAILED",
                    "We could not send the email right now. Please try again in a minute.");
        }
        return rules.resendSeconds();
    }

    /**
     * Checks the latest OTP for the email. Throws ApiException if it is wrong, expired or used up.
     * noRollbackFor: a wrong attempt must still be counted even though we throw an error.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyOtp(String email, String code) {
        OtpCode otp = otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(email)
                .filter(o -> !o.isUsed())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP_NOT_FOUND",
                        "Please ask for an OTP first."));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED",
                    "This OTP has expired. Please ask for a new one.");
        }
        if (otp.getAttempts() >= rules.maxAttempts()) {
            throw tooManyAttempts();
        }
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.recordFailedAttempt();
            int left = rules.maxAttempts() - otp.getAttempts();
            if (left <= 0) {
                throw tooManyAttempts();
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID",
                    "Wrong OTP. " + left + " attempts left.", Map.of("left", left));
        }
        otp.markUsed();
    }

    private static ApiException tooManyAttempts() {
        return new ApiException(HttpStatus.BAD_REQUEST, "OTP_TOO_MANY_ATTEMPTS",
                "Too many wrong attempts. Please ask for a new OTP.");
    }
}
