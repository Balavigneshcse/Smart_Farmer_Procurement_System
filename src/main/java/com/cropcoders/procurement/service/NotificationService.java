package com.cropcoders.procurement.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.dto.BookingResponse;
import com.cropcoders.procurement.security.QrLinkSigner;
import com.cropcoders.procurement.dto.NotificationListResponse;
import com.cropcoders.procurement.dto.NotificationResponse;
import com.cropcoders.procurement.email.EmailQueuedEvent;
import com.cropcoders.procurement.email.EmailTemplates;
import com.cropcoders.procurement.entity.Booking;
import com.cropcoders.procurement.entity.FarmerProfile;
import com.cropcoders.procurement.entity.Notification;
import com.cropcoders.procurement.entity.Slot;
import com.cropcoders.procurement.entity.User;
import com.cropcoders.procurement.enums.EmailStatus;
import com.cropcoders.procurement.enums.NotificationEvent;
import com.cropcoders.procurement.repository.FarmerProfileRepository;
import com.cropcoders.procurement.repository.NotificationRepository;

/**
 * Tells farmers what happened to their booking: always on their dashboard, and by email for key events.
 *
 * Call {@link #notifyBooking} inside the transaction that made the change. The notification is saved
 * in the same transaction; the email is sent in the background only after it commits (EmailDispatcher).
 */
@Service
public class NotificationService {

    private static final Set<EmailStatus> COUNTS_TOWARDS_LIMIT = EnumSet.of(EmailStatus.PENDING, EmailStatus.SENT);
    private static final Locale INDIA = Locale.of("en", "IN");
    private static final int MAX_SUBJECT = 200;
    private static final int MAX_MESSAGE = 500;

    private final NotificationRepository notificationRepository;
    private final FarmerProfileRepository profileRepository;
    private final EmailTemplates templates;
    private final MessageSource messages;
    private final ApplicationEventPublisher events;
    private final int dailyEmailLimit;

    private final QrLinkSigner qrLinks;

    public NotificationService(NotificationRepository notificationRepository, FarmerProfileRepository profileRepository,
                               EmailTemplates templates, MessageSource messages, ApplicationEventPublisher events,
                               AppProperties properties,
                               QrLinkSigner qrLinks) {
        this.qrLinks = qrLinks;
        this.notificationRepository = notificationRepository;
        this.profileRepository = profileRepository;
        this.templates = templates;
        this.messages = messages;
        this.events = events;
        this.dailyEmailLimit = properties.email().dailyLimit();
    }

    /**
     * @param extra the event's own values ({5}, {6} in messages.properties), after the
     *              token, centre, day, slot time and crop that every booking notification gets
     */
    public void notifyBooking(Booking booking, NotificationEvent event, Object... extra) {
        User farmer = booking.getFarmer();
        String language = profileRepository.findById(farmer.getId()).map(FarmerProfile::getLanguage).orElse("en");
        Locale locale = Locale.forLanguageTag(language);

        Object[] args = bookingValues(booking, locale, extra);
        String subject = shorten(messages.getMessage("notify." + event.key() + ".subject", args, locale), MAX_SUBJECT);
        String text = shorten(messages.getMessage("notify." + event.key() + ".text", args, locale), MAX_MESSAGE);

        // Demo farmers (@demo.invalid) are never emailed.
        boolean emailable = event.sendsEmail() && !DemoDataService.isDemoEmail(farmer.getEmail());
        EmailStatus status = !emailable ? EmailStatus.IN_APP_ONLY
                : emailsSentToday() < dailyEmailLimit ? EmailStatus.PENDING
                : EmailStatus.SKIPPED;
        Notification notification = notificationRepository.save(new Notification(farmer.getId(), farmer.getEmail(),
                booking.getId(), event, subject, text, status));

        if (status == EmailStatus.PENDING) {
            // The confirmation email carries the QR code, so the farmer can show it without opening the app.
            boolean withQr = event == NotificationEvent.BOOKING_CONFIRMED;
            events.publishEvent(new EmailQueuedEvent(notification.getId(), templates.notification(farmer.getEmail(),
                    language, farmer.getName(), subject, text, "/farmer/booking.html?id=" + booking.getId(),
                    withQr ? BookingResponse.qrText(booking) : null, withQr ? qrLinks.url(booking.getId()) : null)));
        }
    }

    @Transactional(readOnly = true)
    public NotificationListResponse myNotifications(Long farmerId) {
        List<NotificationResponse> items = notificationRepository.findTop30ByUserIdOrderByCreatedAtDescIdDesc(farmerId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
        return new NotificationListResponse(items, notificationRepository.countByUserIdAndReadAtIsNull(farmerId));
    }

    @Transactional
    public void markAllRead(Long farmerId) {
        notificationRepository.markAllRead(farmerId, LocalDateTime.now());
    }

    /** Amount in Indian format for texts, e.g. ₹58,040.50 */
    public static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(INDIA).format(amount);
    }

    private long emailsSentToday() {
        return notificationRepository.countByEmailStatusInAndCreatedAtGreaterThanEqual(COUNTS_TOWARDS_LIMIT,
                LocalDate.now().atStartOfDay());
    }

    /** {0} token, {1} centre, {2} day, {3} slot time, {4} crop, then the extra values. */
    private static Object[] bookingValues(Booking booking, Locale locale, Object... extra) {
        Slot slot = booking.getSlot();
        DateTimeFormatter day = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale);
        // The language's own time style: "8:00 AM" in English, "முற்பகல் 8:00" in Tamil.
        DateTimeFormatter time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale);

        Object[] values = new Object[5 + extra.length];
        values[0] = booking.getToken();
        values[1] = booking.getCentre().getName();
        values[2] = booking.getBookingDate().format(day);
        values[3] = slot.getStartTime().format(time) + " - " + slot.getEndTime().format(time);
        values[4] = booking.getCrop().nameIn(locale.getLanguage());
        for (int i = 0; i < extra.length; i++) {
            // Numbers are passed as text so MessageFormat doesn't add separators (e.g. token numbers).
            values[5 + i] = String.valueOf(extra[i]);
        }
        return values;
    }

    private static String shorten(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
