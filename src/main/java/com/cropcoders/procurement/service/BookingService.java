package com.cropcoders.procurement.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.dto.BookingEventResponse;
import com.cropcoders.procurement.dto.BookingResponse;
import com.cropcoders.procurement.dto.CreateBookingRequest;
import com.cropcoders.procurement.dto.FeedbackRequest;
import com.cropcoders.procurement.entity.Booking;
import com.cropcoders.procurement.entity.BookingEvent;
import com.cropcoders.procurement.entity.Crop;
import com.cropcoders.procurement.entity.Payment;
import com.cropcoders.procurement.entity.Procurement;
import com.cropcoders.procurement.entity.Slot;
import com.cropcoders.procurement.entity.VisitFeedback;
import com.cropcoders.procurement.enums.BookingEventType;
import com.cropcoders.procurement.enums.BookingStatus;
import com.cropcoders.procurement.enums.NotificationEvent;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.realtime.QueueChangedEvent;
import com.cropcoders.procurement.repository.BookingEventRepository;
import com.cropcoders.procurement.repository.BookingRepository;
import com.cropcoders.procurement.repository.CropRepository;
import com.cropcoders.procurement.repository.PaymentRepository;
import com.cropcoders.procurement.repository.ProcurementRepository;
import com.cropcoders.procurement.repository.SlotRepository;
import com.cropcoders.procurement.repository.UserRepository;
import com.cropcoders.procurement.repository.VisitFeedbackRepository;

/** A farmer booking, viewing and cancelling their slots, and reviewing a visit after the weighment. */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingEventRepository eventRepository;
    private final SlotRepository slotRepository;
    private final CropRepository cropRepository;
    private final UserRepository userRepository;
    private final ProcurementRepository procurementRepository;
    private final PaymentRepository paymentRepository;
    private final VisitFeedbackRepository feedbackRepository;
    private final TokenService tokenService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final int daysAhead;
    private final int reminderBeforeMinutes;

    public BookingService(BookingRepository bookingRepository, BookingEventRepository eventRepository,
                          SlotRepository slotRepository, CropRepository cropRepository, UserRepository userRepository,
                          ProcurementRepository procurementRepository, PaymentRepository paymentRepository,
                          VisitFeedbackRepository feedbackRepository, TokenService tokenService,
                          NotificationService notificationService, ApplicationEventPublisher events,
                          AppProperties properties) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
        this.slotRepository = slotRepository;
        this.cropRepository = cropRepository;
        this.userRepository = userRepository;
        this.procurementRepository = procurementRepository;
        this.paymentRepository = paymentRepository;
        this.feedbackRepository = feedbackRepository;
        this.tokenService = tokenService;
        this.notificationService = notificationService;
        this.events = events;
        this.daysAhead = properties.booking().daysAhead();
        this.reminderBeforeMinutes = properties.booking().reminderBeforeMinutes();
    }

    /**
     * Books a slot. Everything happens in one transaction: if any step fails
     * (slot full, duplicate booking, ...) the place and token are not used up.
     */
    @Transactional
    public BookingResponse create(Long farmerId, CreateBookingRequest request) {
        Slot slot = slotRepository.findWithCentreById(request.slotId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "Slot not found."));
        Crop crop = cropRepository.findById(request.cropId())
                .filter(Crop::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CROP_NOT_FOUND", "Crop not found."));

        LocalDateTime now = LocalDateTime.now();
        LocalDate lastBookableDay = now.toLocalDate().plusDays(daysAhead - 1L);
        if (slot.hasEnded(now) || slot.getSlotDate().isAfter(lastBookableDay) || !slot.getCentre().isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_CLOSED", "This time can no longer be booked.");
        }
        if (bookingRepository.existsByFarmerIdAndBookingDateAndStatusIn(farmerId, slot.getSlotDate(),
                BookingStatus.ACTIVE)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_BOOKING_EXISTS",
                    "You already have a booking on this day. Cancel it first to book another time.");
        }
        if (slotRepository.reserveSeat(slot.getId()) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_FULL",
                    "Sorry, this time just got full. Please choose another time.");
        }

        String token = tokenService.nextToken(slot.getCentre(), slot.getSlotDate());
        Booking booking = bookingRepository.save(
                new Booking(token, userRepository.getReferenceById(farmerId), slot, crop, request.estQtyQtl()));
        BookingEvent event = eventRepository.save(new BookingEvent(booking, BookingEventType.BOOKED,
                "Slot " + slot.getStartTime() + "-" + slot.getEndTime(), farmerId));
        // Booked shortly before the slot: the confirmation already has the time, no separate reminder needed.
        if (!slot.startsAt().isAfter(now.plusMinutes(reminderBeforeMinutes))) {
            booking.setReminderSent(true);
        }
        notificationService.notifyBooking(booking, NotificationEvent.BOOKING_CONFIRMED);
        notifyQueueIfToday(booking);

        return BookingResponse.from(booking, now, List.of(BookingEventResponse.from(event)));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(Long farmerId) {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> bookings = bookingRepository.findAllForFarmer(farmerId);
        Results results = loadResults(bookings);
        return bookings.stream()
                .map(booking -> {
                    Procurement procurement = results.procurementOf(booking);
                    return BookingResponse.from(booking, now, null, procurement, results.paymentOf(procurement),
                            results.feedbackOf(booking));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse myBooking(Long farmerId, Long bookingId) {
        return withTimeline(find(farmerId, bookingId));
    }

    /**
     * Cancels a booking that has not started yet, and gives the place back to the slot.
     * The booking is locked first: a second cancel at the same moment (double click, two tabs) or a check-in
     * at the counter waits, then sees the new status, so the place is given back only once.
     */
    @Transactional
    public BookingResponse cancel(Long farmerId, Long bookingId) {
        bookingRepository.lockForFarmer(bookingId, farmerId).orElseThrow(BookingService::bookingNotFound);
        Booking booking = find(farmerId, bookingId);
        if (!booking.isCancellable(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BOOKING_NOT_CANCELLABLE",
                    "This booking can no longer be cancelled.");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        slotRepository.releaseSeat(booking.getSlot().getId());
        eventRepository.save(new BookingEvent(booking, BookingEventType.CANCELLED, "Cancelled by farmer", farmerId));
        notificationService.notifyBooking(booking, NotificationEvent.BOOKING_CANCELLED);
        notifyQueueIfToday(booking);
        return withTimeline(booking);
    }

    /**
     * The farmer reviews a visit after the crop was weighed (accepted or rejected): stars for the centre and the
     * app, what should improve and optional comments. Only once per booking, and it cannot be changed later.
     * The booking is locked first (like cancel), so a double click waits for the first review and then gets
     * "already reviewed"; the UNIQUE booking_id in the database is the last safety net.
     */
    @Transactional
    public BookingResponse giveFeedback(Long farmerId, Long bookingId, FeedbackRequest request) {
        bookingRepository.lockForFarmer(bookingId, farmerId).orElseThrow(BookingService::bookingNotFound);
        Booking booking = find(farmerId, bookingId);
        if (!booking.getStatus().isWeighed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FEEDBACK_NOT_ALLOWED",
                    "You can review the visit after your crop is weighed.");
        }
        if (feedbackRepository.existsByBookingId(bookingId)) {
            throw feedbackAlreadyGiven();
        }
        try {
            feedbackRepository.saveAndFlush(new VisitFeedback(booking, request.centreRating(), request.appRating(),
                    request.centreIssues(), request.appIssues(), request.centreComment(), request.appComment()));
        } catch (DataIntegrityViolationException e) {
            // Another review of this booking was saved at the same moment (UNIQUE booking_id).
            throw feedbackAlreadyGiven();
        }
        return withTimeline(booking);
    }

    private static ApiException feedbackAlreadyGiven() {
        return new ApiException(HttpStatus.CONFLICT, "FEEDBACK_ALREADY_GIVEN", "You have already reviewed this visit.");
    }

    /** Today's bookings appear on the operator desk ("expected"), so refresh the live screens. */
    private void notifyQueueIfToday(Booking booking) {
        if (booking.getBookingDate().equals(LocalDate.now())) {
            events.publishEvent(new QueueChangedEvent(booking.getCentre().getId()));
        }
    }

    private Booking find(Long farmerId, Long bookingId) {
        return bookingRepository.findForFarmer(bookingId, farmerId).orElseThrow(BookingService::bookingNotFound);
    }

    private static ApiException bookingNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found.");
    }

    private BookingResponse withTimeline(Booking booking) {
        List<BookingEventResponse> events = eventRepository.findByBookingIdOrderByCreatedAtAscIdAsc(booking.getId())
                .stream()
                .map(BookingEventResponse::from)
                .toList();
        Results results = loadResults(List.of(booking));
        Procurement procurement = results.procurementOf(booking);
        return BookingResponse.from(booking, LocalDateTime.now(), events, procurement, results.paymentOf(procurement),
                results.feedbackOf(booking));
    }

    /** Weighment results, payments and reviews for a set of bookings, loaded with three queries. */
    private record Results(Map<Long, Procurement> byBooking, Map<Long, Payment> byProcurement,
                           Map<Long, VisitFeedback> feedbackByBooking) {

        Procurement procurementOf(Booking booking) {
            return byBooking.get(booking.getId());
        }

        Payment paymentOf(Procurement procurement) {
            return procurement == null ? null : byProcurement.get(procurement.getId());
        }

        VisitFeedback feedbackOf(Booking booking) {
            return feedbackByBooking.get(booking.getId());
        }
    }

    private Results loadResults(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return new Results(Map.of(), Map.of(), Map.of());
        }
        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        Map<Long, Procurement> byBooking = procurementRepository.findByBookingIds(bookingIds).stream()
                .collect(Collectors.toMap(p -> p.getBooking().getId(), Function.identity()));
        Map<Long, Payment> byProcurement = byBooking.isEmpty() ? Map.of()
                : paymentRepository.findByProcurementIds(byBooking.values().stream().map(Procurement::getId).toList())
                .stream()
                .collect(Collectors.toMap(p -> p.getProcurement().getId(), Function.identity()));
        // Only weighed bookings can have a review, so the query is skipped when there are none.
        List<Long> weighedIds = bookings.stream().filter(b -> b.getStatus().isWeighed()).map(Booking::getId).toList();
        Map<Long, VisitFeedback> feedback = weighedIds.isEmpty() ? Map.of()
                : feedbackRepository.findByBookingIdIn(weighedIds).stream()
                .collect(Collectors.toMap(VisitFeedback::getBookingId, Function.identity()));
        return new Results(byBooking, byProcurement, feedback);
    }
}
