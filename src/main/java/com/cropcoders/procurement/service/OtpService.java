package com.cropcoders.procurement.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.entity.OtpCode;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.repository.OtpCodeRepository;
import com.cropcoders.procurement.sms.SmsSender;

/**
 * Sends and checks one-time passwords.
 *
 * Rules (from application.yml, app.otp):
 *  - a new OTP only after resend-seconds, and at most max-per-hour per mobile (protects the free SMS quota)
 *  - an OTP is valid for valid-minutes and allows max-attempts wrong tries
 *  - only a BCrypt hash of the code is stored, never the code itself
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository otpCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsSender smsSender;
    private final MessageSource messages;
    private final AppProperties.Otp rules;

    public OtpService(OtpCodeRepository otpCodeRepository, PasswordEncoder passwordEncoder, SmsSender smsSender,
                      MessageSource messages, AppProperties properties) {
        this.otpCodeRepository = otpCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsSender = smsSender;
        this.messages = messages;
        this.rules = properties.otp();
    }

    /** Creates a new OTP and sends it by SMS in the given language. Returns seconds until a resend is allowed. */
    @Transactional
    public int sendOtp(String mobile, String language) {
        LocalDateTime now = LocalDateTime.now();

        otpCodeRepository.findFirstByMobileOrderByCreatedAtDesc(mobile).ifPresent(last -> {
            long waited = Duration.between(last.getCreatedAt(), now).toSeconds();
            if (waited < rules.resendSeconds()) {
                long wait = rules.resendSeconds() - waited;
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN",
                        "Please wait " + wait + " seconds before asking for a new OTP.", Map.of("seconds", wait));
            }
        });
        if (otpCodeRepository.countByMobileAndCreatedAtAfter(mobile, now.minusHours(1)) >= rules.maxPerHour()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LIMIT",
                    "Too many OTP requests. Please try again after one hour.");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otpCodeRepository.save(new OtpCode(mobile, passwordEncoder.encode(code), now,
                now.plusMinutes(rules.validMinutes())));

        String text = messages.getMessage("sms.otp", new Object[]{code, rules.validMinutes()},
                Locale.forLanguageTag(language));
        smsSender.send(mobile, text);
        return rules.resendSeconds();
    }

    /**
     * Checks the latest OTP for the mobile. Throws ApiException if it is wrong, expired or used up.
     * noRollbackFor: a wrong attempt must still be counted even though we throw an error.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyOtp(String mobile, String code) {
        OtpCode otp = otpCodeRepository.findFirstByMobileOrderByCreatedAtDesc(mobile)
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
