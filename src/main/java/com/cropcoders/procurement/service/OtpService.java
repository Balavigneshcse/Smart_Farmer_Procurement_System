package com.cropcoders.procurement.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.email.EmailQueuedEvent;
import com.cropcoders.procurement.email.EmailTemplates;
import com.cropcoders.procurement.entity.OtpCode;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.repository.OtpCodeRepository;

/**
 * Sends and checks one-time passwords (sent by email).
 *
 * Rules (from application.yml, app.otp):
 *  - a new OTP only after resend-seconds, and at most max-per-hour per email (protects the free email quota)
 *  - at most max-per-day OTPs for all email addresses together, so a script trying made-up addresses
 *    cannot use up the day's email quota (OTP emails do not count towards app.email.daily-limit)
 *  - an OTP is valid for valid-minutes and allows max-attempts wrong tries
 *  - only a BCrypt hash of the code is stored, never the code itself
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository otpCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final EmailTemplates templates;
    private final AppProperties.Otp rules;

    public OtpService(OtpCodeRepository otpCodeRepository, PasswordEncoder passwordEncoder,
                      ApplicationEventPublisher events, EmailTemplates templates, AppProperties properties) {
        this.otpCodeRepository = otpCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.templates = templates;
        this.rules = properties.otp();
    }

    /**
     * Creates a new OTP and emails it in the given language. Returns seconds until a resend is allowed.
     * The email is sent in the background right after the OTP is saved (EmailDispatcher), so the
     * farmer sees the "enter OTP" step at once instead of waiting 2-3 seconds for Gmail.
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
        // All addresses together since midnight. Requests at the very same moment can both pass when the
        // count is just below the limit (one or two OTPs too many); that is fine for a daily limit.
        if (otpCodeRepository.countCreatedSince(now.toLocalDate().atStartOfDay()) >= rules.maxPerDay()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_DAILY_LIMIT",
                    "Too many logins today. Please try again tomorrow, or contact the procurement centre.");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otpCodeRepository.save(new OtpCode(email, passwordEncoder.encode(code), now,
                now.plusMinutes(rules.validMinutes())));

        events.publishEvent(new EmailQueuedEvent(null, templates.otp(email, language, code, rules.validMinutes())));
        return rules.resendSeconds();
    }

    /**
     * Checks the latest OTP for the email. Throws ApiException if it is wrong, expired or used up.
     * The OTP row is locked first, so guesses sent at the same moment are checked one after the other
     * and each wrong one is counted (otherwise two could both save "1 wrong try").
     * noRollbackFor: a wrong attempt must still be counted even though we throw an error.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyOtp(String email, String code) {
        OtpCode otp = otpCodeRepository.lockLatestByEmail(email)
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
