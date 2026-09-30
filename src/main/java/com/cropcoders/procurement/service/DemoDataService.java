package com.cropcoders.procurement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cropcoders.procurement.config.AppProperties;
import com.cropcoders.procurement.dto.DemoDataResponse;
import com.cropcoders.procurement.entity.Centre;
import com.cropcoders.procurement.entity.Crop;
import com.cropcoders.procurement.enums.BookingEventType;
import com.cropcoders.procurement.enums.BookingStatus;
import com.cropcoders.procurement.exception.ApiException;
import com.cropcoders.procurement.realtime.QueueChangedEvent;
import com.cropcoders.procurement.repository.CentreRepository;
import com.cropcoders.procurement.repository.CropRepository;

/**
 * Demo data for presentations: 40 demo farmers, a busy day in progress at every centre
 * (served, at the counter, waiting, still to come) and the last 6 days of history for the charts.
 *
 * Demo farmers have email addresses ending in "@demo.invalid" (a domain that can never exist), so
 * they never get real emails and can be removed again without touching real farmers.
 * The data is written directly with SQL so that past times (check-in, weighment, payment) can be set.
 */
@Service
public class DemoDataService {

    public static final String EMAIL_DOMAIN = "@demo.invalid";

    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);
    private static final int HISTORY_DAYS = 6;

    /** Name, language. Most farmers in the delta speak Tamil. */
    private static final String[][] FARMERS = {
            {"Murugan K", "ta"}, {"Lakshmi R", "ta"}, {"Selvam P", "ta"}, {"Kavitha S", "ta"},
            {"Ramasamy A", "ta"}, {"Muthulakshmi V", "ta"}, {"Arumugam T", "ta"}, {"Saraswathi M", "ta"},
            {"Palani S", "ta"}, {"Meena K", "ta"}, {"Karthikeyan R", "ta"}, {"Valli P", "ta"},
            {"Senthil Kumar M", "ta"}, {"Chitra N", "ta"}, {"Raja G", "ta"}, {"Parvathi D", "ta"},
            {"Balasubramanian K", "ta"}, {"Anjali R", "ta"}, {"Ganesan V", "ta"}, {"Revathi S", "ta"},
            {"Subramani P", "ta"}, {"Jaya M", "ta"}, {"Velmurugan A", "ta"}, {"Pushpa K", "ta"},
            {"Manikandan S", "ta"}, {"Deepa R", "ta"}, {"Kannan T", "ta"}, {"Sumathi V", "ta"},
            {"Rajendran M", "ta"}, {"Vijaya L", "ta"}, {"Mohan Raj P", "ta"}, {"Tamilselvi K", "ta"},
            {"Ramesh Yadav", "hi"}, {"Sunita Devi", "hi"}, {"Anil Kumar", "hi"}, {"Priya Sharma", "hi"},
            {"Joseph Arul", "en"}, {"Mary Stella", "en"}, {"Anthony Raj", "en"}, {"Grace Rani", "en"},
    };
    private static final String[] VILLAGES = {
            "Keezhavasal", "Vallam", "Budalur", "Kodavasal", "Needamangalam", "Thiruthuraipoondi", "Keezhvelur",
            "Thirumarugal", "Sikkal", "Orathanadu", "Papanasam", "Mannargudi",
    };

    /** Per centre code: [served, at a counter, waiting, still to come] today; history farmers per day. */
    private static final Map<String, int[]> TODAY_PLAN = Map.of(
            "TNJ", new int[]{7, 2, 5, 4},
            "TVR", new int[]{4, 1, 3, 3},
            "NGT", new int[]{3, 1, 2, 2});
    private static final int[] DEFAULT_PLAN = {3, 1, 2, 2};
    private static final Map<String, Integer> HISTORY_BASE = Map.of("TNJ", 9, "TVR", 6, "NGT", 5);

    private static final String DEMO_FARMER_IDS =
            "SELECT id FROM users WHERE role = 'FARMER' AND email LIKE '%" + EMAIL_DOMAIN + "'";

    private final JdbcTemplate jdbc;
    private final CentreRepository centreRepository;
    private final CropRepository cropRepository;
    private final SlotService slotService;
    private final TokenService tokenService;
    private final ApplicationEventPublisher events;
    private final int lateGraceMinutes;
    private final int noShowAfterMinutes;
    private final int reminderBeforeMinutes;

    public DemoDataService(JdbcTemplate jdbc, CentreRepository centreRepository, CropRepository cropRepository,
                           SlotService slotService, TokenService tokenService, ApplicationEventPublisher events,
                           AppProperties properties) {
        this.jdbc = jdbc;
        this.centreRepository = centreRepository;
        this.cropRepository = cropRepository;
        this.slotService = slotService;
        this.tokenService = tokenService;
        this.events = events;
        this.lateGraceMinutes = properties.queue().lateGraceMinutes();
        this.noShowAfterMinutes = properties.queue().noShowAfterMinutes();
        this.reminderBeforeMinutes = properties.booking().reminderBeforeMinutes();
    }

    public static boolean isDemoEmail(String email) {
        return email != null && email.endsWith(EMAIL_DOMAIN);
    }

    @Transactional(readOnly = true)
    public DemoDataResponse status() {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM (%1$s) f) AS farmers,
                       (SELECT count(*) FROM bookings WHERE farmer_id IN (%1$s) AND booking_date = ?) AS today,
                       (SELECT count(*) FROM bookings WHERE farmer_id IN (%1$s)) AS total""".formatted(DEMO_FARMER_IDS),
                (rs, i) -> new DemoDataResponse(rs.getInt("farmers"), rs.getInt("today"), rs.getInt("total")),
                LocalDate.now());
    }

    /** Removes all demo data. Real farmers, their bookings and payments are never touched. */
    @Transactional
    public DemoDataResponse remove() {
        deleteDemoData();
        refreshScreens();
        return status();
    }

    /** Replaces any old demo data with a fresh demo day for today (same numbers every time on the same day). */
    @Transactional
    public DemoDataResponse load() {
        if (cropRepository.findByActiveTrueOrderByNameEn().isEmpty() || centreRepository.findByActiveTrueOrderByName().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "DEMO_NEEDS_DATA",
                    "Switch on at least one centre and one crop before loading demo data.");
        }
        deleteDemoData();

        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDate today = now.toLocalDate();
        Random random = new Random(today.toEpochDay());
        slotService.generateSlots(today.minusDays(HISTORY_DAYS), HISTORY_DAYS + 1);

        Context ctx = new Context(now, random, createFarmers(now, random), cropRepository.findByActiveTrueOrderByNameEn(),
                operatorByCentre(), adminId());
        List<Centre> centres = centreRepository.findByActiveTrueOrderByName();

        for (int back = HISTORY_DAYS; back >= 1; back--) {
            LocalDate day = today.minusDays(back);
            Set<Long> busy = new HashSet<>();
            for (Centre centre : centres) {
                createHistoryDay(ctx, centre, day, busy);
            }
        }
        Set<Long> busyToday = new HashSet<>();
        for (Centre centre : centres) {
            createToday(ctx, centre, busyToday);
        }

        refreshScreens();
        DemoDataResponse result = status();
        log.info("Demo data loaded: {} farmers, {} bookings today, {} in total",
                result.farmers(), result.bookingsToday(), result.bookingsTotal());
        return result;
    }

    // ================= Building the data =================

    private record Farmer(long id, String language) {
    }

    private record SlotRow(long id, LocalTime start, LocalTime end) {
    }

    /** Everything the builders share: the time "now", one random generator, farmers, crops and staff ids. */
    private record Context(LocalDateTime now, Random random, List<Farmer> farmers, List<Crop> crops,
                           Map<Long, Long> operatorByCentre, long adminId) {
    }

    /** One demo visit, filled in step by step and then written to the database. */
    private static final class Visit {
        Centre centre;
        LocalDate day;
        Farmer farmer;
        Crop crop;
        BigDecimal estQty;
        SlotRow slot;
        BookingStatus status = BookingStatus.BOOKED;
        LocalDateTime createdAt;
        LocalDateTime checkedInAt;
        LocalDateTime calledAt;
        LocalDateTime completedAt;
        LocalDateTime queuePriority;
        Integer counterNo;
        boolean reminderSent;
        boolean turnAlertSent;
        boolean paid;
        LocalDateTime paidAt;
    }

    private void createHistoryDay(Context ctx, Centre centre, LocalDate day, Set<Long> busy) {
        List<SlotRow> slots = slotsOf(centre, day);
        if (slots.isEmpty()) {
            return;
        }
        Random random = ctx.random();
        int count = HISTORY_BASE.getOrDefault(centre.getCode(), 4) + random.nextInt(5);
        for (int k = 0; k < count; k++) {
            Visit v = newVisit(ctx, centre, day, busy);
            if (v == null) {
                return;
            }
            v.slot = takeSeat(slots, k * slots.size() / count);
            if (v.slot == null) {
                return;
            }
            v.createdAt = day.minusDays(1 + random.nextInt(4)).atTime(7 + random.nextInt(13), random.nextInt(60));
            double r = random.nextDouble();
            if (r < 0.07) {
                v.status = BookingStatus.CANCELLED;
                releaseSeat(v.slot);
            } else if (r < 0.16) {
                v.status = BookingStatus.NO_SHOW;
                v.reminderSent = true;
            } else {
                v.reminderSent = true;
                v.checkedInAt = day.atTime(v.slot.start()).minusMinutes(random.nextInt(15));
                v.calledAt = v.checkedInAt.plusMinutes(5 + random.nextInt(26));
                v.completedAt = v.calledAt.plusMinutes(6 + random.nextInt(7));
                v.queuePriority = day.atTime(v.slot.start());
                v.counterNo = 1 + random.nextInt(centre.getCounters());
                v.turnAlertSent = true;
                v.status = r < 0.24 ? BookingStatus.REJECTED : BookingStatus.COMPLETED;
                if (v.status == BookingStatus.COMPLETED) {
                    LocalDateTime paidAt = v.completedAt.plusHours(2 + random.nextInt(20));
                    v.paid = paidAt.isBefore(ctx.now().minusMinutes(30));
                    v.paidAt = v.paid ? paidAt : null;
                }
            }
            save(ctx, v);
        }
    }

    /** Today: some farmers already served, some at the counters, a waiting line and some still to come. */
    private void createToday(Context ctx, Centre centre, Set<Long> busy) {
        List<SlotRow> slots = slotsOf(centre, ctx.now().toLocalDate());
        if (slots.size() < 6) {
            return;
        }
        Random random = ctx.random();
        LocalDateTime now = ctx.now();
        LocalDate today = now.toLocalDate();
        int[] plan = TODAY_PLAN.getOrDefault(centre.getCode(), DEFAULT_PLAN);
        int served = plan[0];
        int atCounter = Math.min(plan[1], centre.getCounters());
        int waiting = plan[2];
        int expected = plan[3];

        // The slot running now (kept away from the ends of the day so there are slots before and after it).
        int current = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (!slots.get(i).start().isAfter(now.toLocalTime())) {
                current = i;
            }
        }
        int anchor = Math.max(3, Math.min(current, slots.size() - 3));

        // A farmer who never came, if a slot ended long enough ago.
        SlotRow early = slots.getFirst();
        if (!today.atTime(early.end()).plusMinutes(noShowAfterMinutes).isAfter(now) && served > 3) {
            Visit v = newVisit(ctx, centre, today, busy);
            if (v != null && (v.slot = takeSeat(slots, 0)) != null) {
                v.status = BookingStatus.NO_SHOW;
                v.reminderSent = true;
                v.createdAt = today.minusDays(2).atTime(18, 10);
                save(ctx, v);
            }
        }

        // Already weighed: the oldest first, about 9 minutes apart.
        for (int j = 0; j < served; j++) {
            Visit v = newVisit(ctx, centre, today, busy);
            if (v == null || (v.slot = takeSeat(slots, anchor - 3 + j % 3)) == null) {
                break;
            }
            v.createdAt = today.minusDays(1 + random.nextInt(3)).atTime(8 + random.nextInt(12), random.nextInt(60));
            v.completedAt = now.minusMinutes((served - j) * 9L + 4);
            v.calledAt = v.completedAt.minusMinutes(7 + random.nextInt(5));
            v.checkedInAt = v.calledAt.minusMinutes(8 + random.nextInt(18));
            v.queuePriority = queuePriority(v, today);
            v.counterNo = 1 + j % centre.getCounters();
            v.reminderSent = true;
            v.turnAlertSent = true;
            v.status = j == 1 && served >= 4 ? BookingStatus.REJECTED : BookingStatus.COMPLETED;
            if (v.status == BookingStatus.COMPLETED && j < served / 2) {
                v.paid = true;
                v.paidAt = v.completedAt.plusMinutes(15 + random.nextInt(10));
                if (v.paidAt.isAfter(now)) {
                    v.paidAt = now.minusMinutes(1);
                }
            }
            save(ctx, v);
        }

        // At a counter right now (counters 1, 2, ... so at least one counter stays free for "Call next").
        for (int c = 1; c <= atCounter; c++) {
            Visit v = newVisit(ctx, centre, today, busy);
            if (v == null || (v.slot = takeSeat(slots, anchor - 1)) == null) {
                break;
            }
            v.createdAt = today.minusDays(1).atTime(9 + random.nextInt(10), random.nextInt(60));
            v.calledAt = now.minusMinutes(2 + random.nextInt(5));
            v.checkedInAt = v.calledAt.minusMinutes(10 + random.nextInt(11));
            v.queuePriority = queuePriority(v, today);
            v.counterNo = c;
            v.status = BookingStatus.CALLED;
            v.reminderSent = true;
            v.turnAlertSent = true;
            save(ctx, v);
        }

        // The waiting line: checked in during the last half hour.
        List<Visit> line = new ArrayList<>();
        for (int w = 0; w < waiting; w++) {
            Visit v = newVisit(ctx, centre, today, busy);
            if (v == null || (v.slot = takeSeat(slots, w < waiting / 2 + 1 ? anchor : anchor + 1)) == null) {
                break;
            }
            v.createdAt = today.minusDays(1 + random.nextInt(2)).atTime(8 + random.nextInt(12), random.nextInt(60));
            v.checkedInAt = now.minusMinutes(3 + random.nextInt(30));
            v.queuePriority = queuePriority(v, today);
            v.status = BookingStatus.CHECKED_IN;
            v.reminderSent = true;
            line.add(v);
        }
        line.sort(Comparator.comparing((Visit v) -> v.queuePriority).thenComparing(v -> v.checkedInAt));
        for (int i = 0; i < line.size(); i++) {
            line.get(i).turnAlertSent = i < 3;
            save(ctx, line.get(i));
        }

        // Still to come later today.
        for (int e = 0; e < expected; e++) {
            Visit v = newVisit(ctx, centre, today, busy);
            if (v == null || (v.slot = takeSeat(slots, anchor + 1 + e % 2)) == null) {
                break;
            }
            v.createdAt = today.minusDays(random.nextInt(3)).atTime(7, 30).plusMinutes(random.nextInt(60));
            if (v.createdAt.isAfter(now)) {
                v.createdAt = now.minusMinutes(20);
            }
            v.reminderSent = !today.atTime(v.slot.start()).isAfter(now.plusMinutes(reminderBeforeMinutes));
            save(ctx, v);
        }
    }

    /** Same rule as QueueService: on time = slot start, later than slot end + grace = arrival time. */
    private LocalDateTime queuePriority(Visit v, LocalDate day) {
        LocalDateTime lateAfter = day.atTime(v.slot.end()).plusMinutes(lateGraceMinutes);
        return v.checkedInAt.isAfter(lateAfter) ? v.checkedInAt : day.atTime(v.slot.start());
    }

    /** A new visit by a demo farmer who has no other visit that day; null if everyone is busy. */
    private Visit newVisit(Context ctx, Centre centre, LocalDate day, Set<Long> busy) {
        List<Farmer> free = ctx.farmers().stream().filter(f -> !busy.contains(f.id())).toList();
        if (free.isEmpty()) {
            return null;
        }
        Visit v = new Visit();
        v.centre = centre;
        v.day = day;
        v.farmer = free.get(ctx.random().nextInt(free.size()));
        busy.add(v.farmer.id());
        v.crop = pickCrop(ctx);
        boolean paddy = v.crop.getNameEn().toLowerCase().contains("paddy");
        v.estQty = BigDecimal.valueOf(paddy ? 15 + ctx.random().nextInt(36) : 5 + ctx.random().nextInt(16));
        return v;
    }

    /** Mostly paddy, as in the Cauvery delta. */
    private static Crop pickCrop(Context ctx) {
        List<Crop> crops = ctx.crops();
        List<Crop> paddy = crops.stream().filter(c -> c.getNameEn().toLowerCase().contains("paddy")).toList();
        if (!paddy.isEmpty() && ctx.random().nextDouble() < 0.75) {
            return paddy.get(ctx.random().nextInt(paddy.size()));
        }
        return crops.get(ctx.random().nextInt(crops.size()));
    }

    // ================= Writing to the database =================

    private void save(Context ctx, Visit v) {
        Random random = ctx.random();
        String token = tokenService.nextToken(v.centre, v.day);
        long operatorId = ctx.operatorByCentre().getOrDefault(v.centre.getId(), ctx.adminId());

        Long bookingId = jdbc.queryForObject("""
                INSERT INTO bookings (token, farmer_id, slot_id, centre_id, booking_date, crop_id, est_qty_qtl, status,
                                      queue_priority, checked_in_at, called_at, counter_no, completed_at,
                                      reminder_sent, turn_alert_sent, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id""", Long.class,
                token, v.farmer.id(), v.slot.id(), v.centre.getId(), v.day, v.crop.getId(), v.estQty,
                v.status.name(), v.queuePriority, v.checkedInAt, v.calledAt, v.counterNo, v.completedAt,
                v.reminderSent, v.turnAlertSent, v.createdAt);

        List<Object[]> timeline = new ArrayList<>();
        timeline.add(event(bookingId, BookingEventType.BOOKED, "Slot " + v.slot.start() + "-" + v.slot.end(),
                v.farmer.id(), v.createdAt));
        switch (v.status) {
            case CANCELLED -> timeline.add(event(bookingId, BookingEventType.CANCELLED, "Cancelled by farmer",
                    v.farmer.id(), v.createdAt.plusHours(3)));
            case NO_SHOW -> timeline.add(event(bookingId, BookingEventType.NO_SHOW,
                    "Did not arrive by " + noShowAfterMinutes + " minutes after the slot", null,
                    v.day.atTime(v.slot.end()).plusMinutes(noShowAfterMinutes)));
            default -> {
                // arrived
            }
        }
        if (v.checkedInAt != null) {
            timeline.add(event(bookingId, BookingEventType.CHECKED_IN, "Checked in", operatorId, v.checkedInAt));
        }
        if (v.calledAt != null) {
            timeline.add(event(bookingId, BookingEventType.CALLED, "Counter " + v.counterNo, operatorId, v.calledAt));
        }
        if (v.status == BookingStatus.COMPLETED || v.status == BookingStatus.REJECTED) {
            saveWeighment(ctx, v, bookingId, operatorId, timeline, random);
        }
        jdbc.batchUpdate("""
                INSERT INTO booking_events (booking_id, event, note, created_by, created_at)
                VALUES (?, ?, ?, ?, ?)""", timeline);
    }

    /** Weight, moisture and grade like a real weighment; accepted crops get a payment (paid or pending). */
    private void saveWeighment(Context ctx, Visit v, long bookingId, long operatorId, List<Object[]> timeline,
                               Random random) {
        boolean accepted = v.status == BookingStatus.COMPLETED;
        BigDecimal limit = v.crop.getMaxMoisturePct();
        BigDecimal weight = v.estQty.multiply(BigDecimal.valueOf(0.9 + random.nextDouble() * 0.2))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal moisture = limit.add(BigDecimal.valueOf(accepted ? -(0.5 + random.nextDouble() * 3)
                : 1 + random.nextDouble() * 3)).setScale(1, RoundingMode.HALF_UP);
        String grade = accepted ? (random.nextDouble() < 0.6 ? "A" : random.nextDouble() < 0.8 ? "B" : "C") : "C";
        BigDecimal rate = v.crop.getRatePerQtl();
        BigDecimal amount = accepted ? weight.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        String remarks = accepted ? null : "Moisture " + moisture + "% is above the " + limit + "% limit";

        Long procurementId = jdbc.queryForObject("""
                INSERT INTO procurements (booking_id, weight_qtl, moisture_pct, grade, rate_per_qtl, amount, accepted,
                                          remarks, recorded_by, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id""", Long.class,
                bookingId, weight, moisture, grade, rate, amount, accepted, remarks, operatorId, v.completedAt);

        String details = weight + " qtl, moisture " + moisture + "%, grade " + grade;
        if (!accepted) {
            timeline.add(event(bookingId, BookingEventType.REJECTED, details + " - " + remarks, operatorId,
                    v.completedAt));
            return;
        }
        timeline.add(event(bookingId, BookingEventType.COMPLETED, details + ", Rs " + amount, operatorId,
                v.completedAt));
        timeline.add(event(bookingId, BookingEventType.PAYMENT_PENDING, "Rs " + amount, null, v.completedAt));

        String paymentRef = v.paid ? "pay_DEMO" + randomCode(random, 10) : null;
        jdbc.update("""
                INSERT INTO payments (procurement_id, amount_paise, status, rzp_order_id, rzp_payment_id, paid_at,
                                      created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                procurementId, amount.movePointRight(2).longValueExact(), v.paid ? "PAID" : "PENDING",
                v.paid ? "order_DEMO" + randomCode(random, 10) : null, paymentRef, v.paidAt, v.completedAt);
        if (v.paid) {
            timeline.add(event(bookingId, BookingEventType.PAID, "Rs " + amount + ", ref " + paymentRef,
                    ctx.adminId(), v.paidAt));
        }
    }

    private static Object[] event(long bookingId, BookingEventType type, String note, Long by, LocalDateTime at) {
        return new Object[]{bookingId, type.name(), note, by, at};
    }

    private List<Farmer> createFarmers(LocalDateTime now, Random random) {
        List<Centre> centres = centreRepository.findByActiveTrueOrderByName();
        List<Farmer> farmers = new ArrayList<>();
        for (int i = 0; i < FARMERS.length; i++) {
            String name = FARMERS[i][0];
            String language = FARMERS[i][1];
            String email = "farmer%02d%s".formatted(i + 1, EMAIL_DOMAIN);
            String mobile = "90000100%02d".formatted(i + 1);
            LocalDateTime joined = now.minusDays(20 + random.nextInt(60));

            Long id = jdbc.queryForObject("""
                    INSERT INTO users (name, role, email, mobile, active, created_at)
                    VALUES (?, 'FARMER', ?, ?, TRUE, ?) RETURNING id""", Long.class, name, email, mobile, joined);
            String district = centres.isEmpty() ? "Thanjavur" : centres.get(i % centres.size()).getDistrict();
            jdbc.update("""
                    INSERT INTO farmer_profiles (user_id, address, district, aadhaar_last4, language, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)""",
                    id, (10 + random.nextInt(190)) + ", Main Road, " + VILLAGES[i % VILLAGES.length], district,
                    "%04d".formatted(random.nextInt(10_000)), language, joined);
            farmers.add(new Farmer(id, language));
        }
        return farmers;
    }

    /** The operator of each centre records its check-ins and weighments. */
    private Map<Long, Long> operatorByCentre() {
        Map<Long, Long> operators = new HashMap<>();
        jdbc.query("SELECT centre_id, min(id) AS id FROM users WHERE role = 'OPERATOR' GROUP BY centre_id",
                rs -> {
                    operators.put(rs.getLong("centre_id"), rs.getLong("id"));
                });
        return operators;
    }

    /** The admin "pays" the demo payments. */
    private long adminId() {
        Long id = jdbc.queryForObject("SELECT min(id) FROM users WHERE role IN ('ADMIN', 'SUPER_ADMIN')", Long.class);
        if (id == null) {
            throw new IllegalStateException("Demo data needs an admin account (see V5__roles_and_staff.sql)");
        }
        return id;
    }

    private List<SlotRow> slotsOf(Centre centre, LocalDate day) {
        return jdbc.query("""
                SELECT id, start_time, end_time FROM slots
                WHERE centre_id = ? AND slot_date = ? ORDER BY start_time""",
                (rs, i) -> new SlotRow(rs.getLong("id"), rs.getObject("start_time", LocalTime.class),
                        rs.getObject("end_time", LocalTime.class)),
                centre.getId(), day);
    }

    /** Takes a place in the preferred slot, or the nearest slot that still has room. */
    private SlotRow takeSeat(List<SlotRow> slots, int preferred) {
        int start = Math.max(0, Math.min(preferred, slots.size() - 1));
        for (int step = 0; step < slots.size(); step++) {
            int[] candidates = step == 0 ? new int[]{start} : new int[]{start + step, start - step};
            for (int index : candidates) {
                if (index < 0 || index >= slots.size()) {
                    continue;
                }
                SlotRow slot = slots.get(index);
                // Same safe statement as a real booking: only succeeds while the slot has room.
                if (jdbc.update("UPDATE slots SET booked = booked + 1 WHERE id = ? AND booked < capacity",
                        slot.id()) == 1) {
                    return slot;
                }
            }
        }
        return null;
    }

    private void releaseSeat(SlotRow slot) {
        jdbc.update("UPDATE slots SET booked = booked - 1 WHERE id = ? AND booked > 0", slot.id());
    }

    private static String randomCode(Random random, int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

    // ================= Removing =================

    private void deleteDemoData() {
        String bookings = "SELECT id FROM bookings WHERE farmer_id IN (" + DEMO_FARMER_IDS + ")";
        String procurements = "SELECT id FROM procurements WHERE booking_id IN (" + bookings + ")";

        List<Long> slotIds = jdbc.queryForList(
                "SELECT DISTINCT slot_id FROM bookings WHERE farmer_id IN (" + DEMO_FARMER_IDS + ")", Long.class);

        jdbc.update("DELETE FROM notifications WHERE user_id IN (" + DEMO_FARMER_IDS + ") OR booking_id IN ("
                + bookings + ")");
        jdbc.update("DELETE FROM payments WHERE procurement_id IN (" + procurements + ")");
        jdbc.update("DELETE FROM procurements WHERE booking_id IN (" + bookings + ")");
        jdbc.update("DELETE FROM bookings WHERE farmer_id IN (" + DEMO_FARMER_IDS + ")");   // timeline goes too (cascade)
        jdbc.update("DELETE FROM otp_codes WHERE email LIKE '%" + EMAIL_DOMAIN + "'");
        jdbc.update("DELETE FROM users WHERE id IN (" + DEMO_FARMER_IDS + ")");            // profiles go too (cascade)

        // Give the places back: each slot's count = the bookings that are left in it.
        if (!slotIds.isEmpty()) {
            jdbc.batchUpdate("""
                    UPDATE slots SET booked = (SELECT count(*) FROM bookings b
                                               WHERE b.slot_id = slots.id AND b.status <> 'CANCELLED')
                    WHERE id = ?""", slotIds.stream().map(id -> new Object[]{id}).collect(Collectors.toList()));
        }
        // Token numbers start again at 001 on days that no longer have any booking.
        jdbc.update("""
                DELETE FROM token_counters tc
                WHERE NOT EXISTS (SELECT 1 FROM bookings b WHERE b.centre_id = tc.centre_id AND b.booking_date = tc.day)""");
    }

    private void refreshScreens() {
        centreRepository.findAll().forEach(centre -> events.publishEvent(new QueueChangedEvent(centre.getId())));
    }
}
