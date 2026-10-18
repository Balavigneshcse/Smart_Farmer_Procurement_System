package com.cropcoders.procurement.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.dto.CentreResponse;
import com.cropcoders.procurement.dto.OperatorActionResponse;
import com.cropcoders.procurement.dto.OperatorBookingView;
import com.cropcoders.procurement.dto.OperatorQueueResponse;
import com.cropcoders.procurement.dto.PublicQueueResponse;
import com.cropcoders.procurement.entity.Booking;
import com.cropcoders.procurement.entity.BookingEvent;
import com.cropcoders.procurement.entity.Centre;
import com.cropcoders.procurement.entity.FarmerProfile;
import com.cropcoders.procurement.enums.BookingEventType;
import com.cropcoders.procurement.enums.BookingStatus;
import com.cropcoders.procurement.enums.NotificationEvent;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.realtime.QueueChangedEvent;
import com.cropcoders.procurement.repository.BookingEventRepository;
import com.cropcoders.procurement.repository.BookingRepository;
import com.cropcoders.procurement.repository.CentreRepository;
import com.cropcoders.procurement.repository.FarmerProfileRepository;
import com.cropcoders.procurement.security.AuthUser;

/**
 * The live queue at a centre.
 *
 * Queue order: farmers who arrive on time (or early) are ordered by their slot start time;
 * a farmer who arrives after "slot end + grace" is placed by their arrival time (back of the line).
 * All of this is stored in bookings.queue_priority when the farmer checks in.
 */
@Service
public class QueueService {

    private static final String QR_PREFIX = "SFPS:";

    /** Farmers this close to the front get a "your turn is near" email (once per visit). */
    private static final int TURN_NEAR_POSITION = 3;

    /** Waiting line order: queue priority, then who checked in first. */
    private static final Comparator<Booking> QUEUE_ORDER =
            Comparator.comparing(Booking::getQueuePriority).thenComparing(Booking::getCheckedInAt);

    private final BookingRepository bookingRepository;
    private final BookingEventRepository eventRepository;
    private final CentreRepository centreRepository;
    private final FarmerProfileRepository profileRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final int lateGraceMinutes;

    public QueueService(BookingRepository bookingRepository, BookingEventRepository eventRepository,
                        CentreRepository centreRepository, FarmerProfileRepository profileRepository,
                        NotificationService notificationService, ApplicationEventPublisher events,
                        AppProperties properties) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
        this.centreRepository = centreRepository;
        this.profileRepository = profileRepository;
        this.notificationService = notificationService;
        this.events = events;
        this.lateGraceMinutes = properties.queue().lateGraceMinutes();
    }

    // ================= Operator actions =================

    /** The farmer arrived: find their booking by token or QR text and put them in the waiting line. */
    @Transactional
    public OperatorActionResponse checkIn(AuthUser operator, String code) {
        Centre centre = operatorCentre(operator);
        Booking booking = findTodaysBooking(centre, code.trim().toUpperCase());

        switch (booking.getStatus()) {
            case BOOKED -> { /* ok */ }
            case CHECKED_IN, CALLED -> throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CHECKED_IN",
                    booking.getToken() + " is already checked in.");
            default -> throw new ApiException(HttpStatus.CONFLICT, "BOOKING_CLOSED",
                    booking.getToken() + " is " + booking.getStatus().name().toLowerCase().replace('_', ' ') + ".");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean late = isLate(booking, now);
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setCheckedInAt(now);
        booking.setQueuePriority(late ? now : booking.getSlot().startsAt());
        eventRepository.save(new BookingEvent(booking, BookingEventType.CHECKED_IN,
                late ? "Late arrival - joined the back of the queue" : "Checked in", operator.id()));

        OperatorActionResponse response = afterChange(centre, booking,
                "Checked in " + booking.getToken() + (late ? " (late arrival)" : ""), true);
        notificationService.notifyBooking(booking, NotificationEvent.CHECKED_IN,
                response.booking().position(), response.booking().etaMinutes());
        return response;
    }

    /** Calls the first farmer in the waiting line to a free counter. */
    @Transactional
    public OperatorActionResponse callNext(AuthUser operator, int counterNo) {
        Centre centre = operatorCentre(operator);
        if (counterNo < 1 || counterNo > centre.getCounters()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUNTER",
                    "This centre has counters 1 to " + centre.getCounters() + ".");
        }
        LocalDate today = LocalDate.now();
        if (bookingRepository.existsByCentreIdAndBookingDateAndStatusAndCounterNo(centre.getId(), today,
                BookingStatus.CALLED, counterNo)) {
            throw counterBusy(counterNo);
        }
        Booking next = bookingRepository.lockNextWaiting(centre.getId(), today)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "QUEUE_EMPTY", "No farmers are waiting."));

        next.setStatus(BookingStatus.CALLED);
        next.setCounterNo(counterNo);
        next.setCalledAt(LocalDateTime.now());
        // Two "Call next" clicks for the same counter at the same moment both pass the check above.
        // Writing now lets the database index uq_counter_serving refuse the second one (V8).
        try {
            bookingRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw counterBusy(counterNo);
        }
        eventRepository.save(new BookingEvent(next, BookingEventType.CALLED, "Counter " + counterNo, operator.id()));
        notificationService.notifyBooking(next, NotificationEvent.CALLED, counterNo);

        return afterChange(centre, next, "Called " + next.getToken() + " to counter " + counterNo);
    }

    /** Calls the same farmer again (the TV announces them again). */
    @Transactional
    public OperatorActionResponse recall(AuthUser operator, Long bookingId) {
        Centre centre = operatorCentre(operator);
        Booking booking = bookingAtCounter(centre, bookingId);
        booking.setCalledAt(LocalDateTime.now());
        return afterChange(centre, booking,
                "Called " + booking.getToken() + " again to counter " + booking.getCounterNo());
    }

    /** The farmer did not come to the counter: send them to the back of the waiting line. */
    @Transactional
    public OperatorActionResponse skip(AuthUser operator, Long bookingId) {
        Centre centre = operatorCentre(operator);
        Booking booking = bookingAtCounter(centre, bookingId);
        int counterNo = booking.getCounterNo();

        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setCounterNo(null);
        booking.setCalledAt(null);
        booking.setQueuePriority(LocalDateTime.now());
        booking.setTurnAlertSent(false);     // tell them again when they are near the front
        eventRepository.save(new BookingEvent(booking, BookingEventType.SKIPPED,
                "Not at counter " + counterNo + " when called", operator.id()));

        return afterChange(centre, booking, booking.getToken() + " moved to the back of the queue");
    }

    @Transactional(readOnly = true)
    public OperatorQueueResponse operatorView(AuthUser operator) {
        Centre centre = operatorCentre(operator);
        return toOperatorView(loadDay(centre));
    }

    // ================= Public queue (TV, farmer phone) =================

    @Transactional(readOnly = true)
    public PublicQueueResponse publicSnapshot(Long centreId) {
        Centre centre = centreRepository.findById(centreId)
                .filter(Centre::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CENTRE_NOT_FOUND", "Centre not found."));
        DayState day = loadDay(centre);

        List<PublicQueueResponse.ServingView> serving = new ArrayList<>();
        for (int counter = 1; counter <= centre.getCounters(); counter++) {
            Booking b = day.serving().get(counter);
            serving.add(new PublicQueueResponse.ServingView(counter, b == null ? null : b.getToken(),
                    b == null ? null : b.getCalledAt()));
        }
        List<PublicQueueResponse.WaitingView> waiting = new ArrayList<>();
        for (int i = 0; i < day.waiting().size(); i++) {
            waiting.add(new PublicQueueResponse.WaitingView(i + 1, day.waiting().get(i).getToken(), day.etaFor(i + 1)));
        }
        return new PublicQueueResponse(centre.getId(), centre.getCode(), centre.getName(), centre.getCounters(),
                day.avgServiceMinutes(), serving, waiting, day.served(), day.avgWaitMinutes(), LocalDateTime.now());
    }

    // ================= Helpers =================

    /** Everything about one centre's queue today, worked out from today's bookings. */
    private record DayState(Centre centre, List<Booking> waiting, Map<Integer, Booking> serving,
                            List<Booking> expected, int served, Integer avgWaitMinutes, int avgServiceMinutes) {

        int freeCounters() {
            return centre.getCounters() - serving.size();
        }

        int etaFor(int position) {
            return EtaCalculator.etaMinutes(position, centre.getCounters(), freeCounters(), avgServiceMinutes);
        }
    }

    private DayState loadDay(Centre centre) {
        List<Booking> all = bookingRepository.findAllForCentreDay(centre.getId(), LocalDate.now());

        List<Booking> waiting = all.stream()
                .filter(b -> b.getStatus() == BookingStatus.CHECKED_IN)
                .sorted(QUEUE_ORDER)
                .toList();
        Map<Integer, Booking> serving = all.stream()
                .filter(b -> b.getStatus() == BookingStatus.CALLED && b.getCounterNo() != null)
                .collect(Collectors.toMap(Booking::getCounterNo, Function.identity(), (a, b) -> a, TreeMap::new));
        List<Booking> expected = all.stream()
                .filter(b -> b.getStatus() == BookingStatus.BOOKED)
                .sorted(Comparator.comparing((Booking b) -> b.getSlot().getStartTime()).thenComparing(Booking::getToken))
                .toList();
        List<Booking> done = all.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED || b.getStatus() == BookingStatus.REJECTED)
                .toList();

        // Real service times of the last 10 farmers served today drive the ETA.
        List<Double> recentServiceMinutes = done.stream()
                .filter(b -> b.getCalledAt() != null && b.getCompletedAt() != null)
                .sorted(Comparator.comparing(Booking::getCompletedAt).reversed())
                .limit(10)
                .map(b -> minutesBetween(b.getCalledAt(), b.getCompletedAt()))
                .toList();
        int avgService = EtaCalculator.averageServiceMinutes(recentServiceMinutes, centre.getAvgServiceMin());

        // Average wait today = from check-in until called to a counter.
        OptionalDouble avgWait = all.stream()
                .filter(b -> b.getCheckedInAt() != null && b.getCalledAt() != null)
                .mapToDouble(b -> minutesBetween(b.getCheckedInAt(), b.getCalledAt()))
                .average();

        return new DayState(centre, waiting, serving, expected, done.size(),
                avgWait.isPresent() ? (int) Math.round(avgWait.getAsDouble()) : null, avgService);
    }

    private OperatorQueueResponse toOperatorView(DayState day) {
        Map<Long, FarmerProfile> profiles = profilesOf(day);

        List<OperatorQueueResponse.CounterView> counters = new ArrayList<>();
        for (int counter = 1; counter <= day.centre().getCounters(); counter++) {
            Booking b = day.serving().get(counter);
            counters.add(new OperatorQueueResponse.CounterView(counter,
                    b == null ? null : view(b, profiles, null, null)));
        }
        List<OperatorBookingView> waiting = new ArrayList<>();
        for (int i = 0; i < day.waiting().size(); i++) {
            waiting.add(view(day.waiting().get(i), profiles, i + 1, day.etaFor(i + 1)));
        }
        List<OperatorBookingView> expected = day.expected().stream()
                .map(b -> view(b, profiles, null, null))
                .toList();

        var stats = new OperatorQueueResponse.Stats(expected.size(), waiting.size(), day.serving().size(),
                day.served(), day.avgWaitMinutes(), day.avgServiceMinutes());
        return new OperatorQueueResponse(CentreResponse.from(day.centre()), counters, waiting, expected, stats);
    }

    /**
     * Tells the live screens about the change, emails farmers who are now near the front,
     * and returns the updated operator desk. Also used by ProcurementService.
     */
    OperatorActionResponse afterChange(Centre centre, Booking booking, String message) {
        return afterChange(centre, booking, message, false);
    }

    /** justCheckedIn: the booking's own check-in message already gives its place in the queue. */
    private OperatorActionResponse afterChange(Centre centre, Booking booking, String message, boolean justCheckedIn) {
        // Screens are refreshed after this transaction commits (see QueueSseHub).
        events.publishEvent(new QueueChangedEvent(centre.getId()));

        DayState day = loadDay(centre);
        alertFarmersNearTheFront(day, justCheckedIn ? booking : null);
        Integer position = null;
        Integer eta = null;
        int index = day.waiting().indexOf(booking);
        if (index >= 0) {
            position = index + 1;
            eta = day.etaFor(position);
        }
        OperatorBookingView view = view(booking, profilesOf(day), position, eta);
        return new OperatorActionResponse(message, view, toOperatorView(day));
    }

    /** The first few farmers in the waiting line get one "your turn is near" email each. */
    private void alertFarmersNearTheFront(DayState day, Booking justCheckedIn) {
        int count = Math.min(TURN_NEAR_POSITION, day.waiting().size());
        for (int i = 0; i < count; i++) {
            Booking b = day.waiting().get(i);
            if (b.isTurnAlertSent()) {
                continue;
            }
            b.setTurnAlertSent(true);
            if (b != justCheckedIn) {
                notificationService.notifyBooking(b, NotificationEvent.TURN_NEAR, i + 1, day.etaFor(i + 1));
            }
        }
    }

    private OperatorBookingView view(Booking b, Map<Long, FarmerProfile> profiles, Integer position, Integer eta) {
        FarmerProfile profile = profiles.get(b.getFarmer().getId());
        return new OperatorBookingView(
                b.getId(),
                b.getToken(),
                b.getStatus(),
                b.getFarmer().getName(),
                b.getFarmer().getMobile(),
                profile == null ? null : profile.getAadhaarLast4(),
                b.getCrop().getNameEn(),
                b.getCrop().getRatePerQtl(),
                b.getCrop().getMaxMoisturePct(),
                b.getEstQtyQtl(),
                b.getSlot().getStartTime(),
                b.getSlot().getEndTime(),
                b.getCheckedInAt(),
                b.getCalledAt(),
                b.getCounterNo(),
                b.getCheckedInAt() != null && isLate(b, b.getCheckedInAt()),
                position,
                eta);
    }

    private Map<Long, FarmerProfile> profilesOf(DayState day) {
        List<Long> farmerIds = new ArrayList<>();
        day.waiting().forEach(b -> farmerIds.add(b.getFarmer().getId()));
        day.serving().values().forEach(b -> farmerIds.add(b.getFarmer().getId()));
        day.expected().forEach(b -> farmerIds.add(b.getFarmer().getId()));
        return profileRepository.findAllById(farmerIds).stream()
                .collect(Collectors.toMap(FarmerProfile::getUserId, Function.identity()));
    }

    private boolean isLate(Booking booking, LocalDateTime arrivedAt) {
        return arrivedAt.isAfter(booking.getSlot().endsAt().plusMinutes(lateGraceMinutes));
    }

    Centre operatorCentre(AuthUser operator) {
        if (operator.centreId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_CENTRE", "Your account is not linked to a centre.");
        }
        return centreRepository.findById(operator.centreId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NO_CENTRE", "Your centre was not found."));
    }

    /**
     * code is either a token ("TNJ-005") or the QR text ("SFPS:12:TNJ-005").
     * The booking must be for this centre and for today.
     */
    private Booking findTodaysBooking(Centre centre, String code) {
        LocalDate today = LocalDate.now();
        Booking booking;
        if (code.startsWith(QR_PREFIX)) {
            String[] parts = code.split(":");
            if (parts.length != 3 || !parts[1].matches("\\d{1,18}")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CODE", "This QR code is not a booking QR code.");
            }
            booking = bookingRepository.findDetailedById(Long.valueOf(parts[1]))
                    .filter(b -> b.getToken().equals(parts[2]))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOKEN_NOT_FOUND",
                            "No booking found for this QR code."));
        } else {
            booking = bookingRepository.findDetailedByToken(centre.getId(), today, code)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOKEN_NOT_FOUND",
                            "No booking with token " + code + " at this centre today."));
        }
        if (!booking.getCentre().getId().equals(centre.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_CENTRE",
                    booking.getToken() + " is booked at " + booking.getCentre().getName() + ".");
        }
        if (!booking.getBookingDate().equals(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_TODAY",
                    booking.getToken() + " is booked for " + booking.getBookingDate() + ", not today.");
        }
        return booking;
    }

    /** A booking of this centre that is currently at a counter (status CALLED). */
    Booking bookingAtCounter(Centre centre, Long bookingId) {
        return bookingRepository.findDetailedById(bookingId)
                .filter(b -> b.getCentre().getId().equals(centre.getId()))
                .filter(b -> b.getStatus() == BookingStatus.CALLED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NOT_AT_COUNTER",
                        "This farmer is not at a counter any more."));
    }

    private static ApiException counterBusy(int counterNo) {
        return new ApiException(HttpStatus.CONFLICT, "COUNTER_BUSY",
                "Counter " + counterNo + " is still serving a farmer. Finish or skip them first.");
    }

    private static double minutesBetween(LocalDateTime from, LocalDateTime to) {
        return Duration.between(from, to).toSeconds() / 60.0;
    }
}
