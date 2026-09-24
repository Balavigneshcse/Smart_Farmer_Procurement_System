package com.cropcoders.procurement.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.entity.Booking;
import com.cropcoders.procurement.entity.BookingEvent;
import com.cropcoders.procurement.enums.BookingEventType;
import com.cropcoders.procurement.enums.BookingStatus;
import com.cropcoders.procurement.enums.NotificationEvent;
import com.cropcoders.procurement.realtime.QueueChangedEvent;
import com.cropcoders.procurement.repository.BookingEventRepository;
import com.cropcoders.procurement.repository.BookingRepository;

/**
 * Work nobody has to do by hand (run by AutomationJob every 15 minutes):
 *  - remind farmers by email shortly before their slot
 *  - close bookings of farmers who never came (NO_SHOW), so the day's lists stay correct
 */
@Service
public class AutomationService {

    /** No-show emails only for slots that ended within this time (not for old ones after a long server sleep). */
    private static final int NO_SHOW_EMAIL_WITHIN_HOURS = 24;

    private final BookingRepository bookingRepository;
    private final BookingEventRepository eventRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final int reminderBeforeMinutes;
    private final int noShowAfterMinutes;

    public AutomationService(BookingRepository bookingRepository, BookingEventRepository eventRepository,
                             NotificationService notificationService, ApplicationEventPublisher events,
                             AppProperties properties) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
        this.events = events;
        this.reminderBeforeMinutes = properties.booking().reminderBeforeMinutes();
        this.noShowAfterMinutes = properties.queue().noShowAfterMinutes();
    }

    /** Emails every farmer whose slot starts within the next reminderBeforeMinutes. Returns how many. */
    @Transactional
    public int sendReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusMinutes(reminderBeforeMinutes);
        // Only today's slots; late in the evening the window would run past midnight.
        LocalTime untilTime = until.toLocalDate().isAfter(now.toLocalDate()) ? LocalTime.MAX : until.toLocalTime();

        List<Booking> due = bookingRepository.findDueForReminder(now.toLocalDate(), now.toLocalTime(), untilTime);
        for (Booking booking : due) {
            booking.setReminderSent(true);
            notificationService.notifyBooking(booking, NotificationEvent.REMINDER);
        }
        return due.size();
    }

    /** Marks bookings as NO_SHOW when the farmer has not checked in by slot end + noShowAfterMinutes. */
    @Transactional
    public int markNoShows() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(noShowAfterMinutes);

        List<Booking> missed = bookingRepository.findNotArrivedBefore(cutoff.toLocalDate(), cutoff.toLocalTime());
        Set<Long> centresToRefresh = new HashSet<>();
        for (Booking booking : missed) {
            booking.setStatus(BookingStatus.NO_SHOW);
            eventRepository.save(new BookingEvent(booking, BookingEventType.NO_SHOW,
                    "Did not arrive by " + noShowAfterMinutes + " minutes after the slot", null));
            if (booking.getSlot().endsAt().isAfter(now.minusHours(NO_SHOW_EMAIL_WITHIN_HOURS))) {
                notificationService.notifyBooking(booking, NotificationEvent.NO_SHOW);
            }
            if (booking.getBookingDate().equals(now.toLocalDate())) {
                centresToRefresh.add(booking.getCentre().getId());   // leaves the operator's "expected" list
            }
        }
        centresToRefresh.forEach(centreId -> events.publishEvent(new QueueChangedEvent(centreId)));
        return missed.size();
    }
}
