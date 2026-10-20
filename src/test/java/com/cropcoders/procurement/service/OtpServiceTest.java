package com.cropcoders.procurement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cropcoders.procurement.TestProperties;
import com.cropcoders.procurement.email.EmailQueuedEvent;
import com.cropcoders.procurement.email.EmailTemplates;
import com.cropcoders.procurement.entity.OtpCode;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.repository.OtpCodeRepository;

/** The OTP limits, with the database and email replaced by Mockito mocks. */
class OtpServiceTest {

    private static final String EMAIL = "ravi@example.com";
    private static final int MAX_PER_DAY = 200;      // TestProperties, like application.yml

    private final OtpCodeRepository repository = mock(OtpCodeRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final OtpService otpService = new OtpService(repository, mock(PasswordEncoder.class), events,
            mock(EmailTemplates.class), TestProperties.withJwtSecret("test-secret-that-is-long-enough-0123456789"));

    @BeforeEach
    void thisAddressHasNoRecentOtp() {
        when(repository.findFirstByEmailOrderByCreatedAtDesc(EMAIL)).thenReturn(Optional.empty());
        when(repository.countByEmailAndCreatedAtAfter(eq(EMAIL), any())).thenReturn(0L);
    }

    @Test
    void dailyLimitForAllAddressesStopsNewOtps() {
        when(repository.countCreatedSince(any())).thenReturn((long) MAX_PER_DAY);

        assertThatThrownBy(() -> otpService.sendOtp(EMAIL, "en"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getCode()).isEqualTo("OTP_DAILY_LIMIT");
                });
        verify(repository, never()).save(any(OtpCode.class));
        verifyNoInteractions(events);                 // no email
    }

    @Test
    void belowTheDailyLimitTheOtpIsSavedAndEmailed() {
        when(repository.countCreatedSince(any())).thenReturn((long) MAX_PER_DAY - 1);

        assertThat(otpService.sendOtp(EMAIL, "en")).isEqualTo(60);   // resend after 60 seconds

        verify(repository).save(any(OtpCode.class));
        verify(events).publishEvent(any(EmailQueuedEvent.class));
    }

    @Test
    void dailyLimitCountsFromMidnight() {
        when(repository.countCreatedSince(any())).thenReturn(0L);

        otpService.sendOtp(EMAIL, "en");

        verify(repository).countCreatedSince(LocalDate.now().atStartOfDay());
    }

    @Test
    void limitPerAddressIsCheckedFirst() {
        when(repository.countByEmailAndCreatedAtAfter(eq(EMAIL), any(LocalDateTime.class))).thenReturn(5L);

        assertThatThrownBy(() -> otpService.sendOtp(EMAIL, "en"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("OTP_LIMIT"));
        verify(repository, never()).countCreatedSince(any());
        verify(repository, never()).save(any(OtpCode.class));
        verifyNoInteractions(events);
    }
}
