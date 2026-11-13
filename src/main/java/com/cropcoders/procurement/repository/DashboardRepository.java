package com.cropcoders.procurement.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cropcoders.procurement.dto.AdminDashboardResponse.CentreToday;
import com.cropcoders.procurement.dto.AdminDashboardResponse.CropTotal;
import com.cropcoders.procurement.dto.AdminDashboardResponse.DayTotal;
import com.cropcoders.procurement.dto.AdminDashboardResponse.Farmers;
import com.cropcoders.procurement.dto.AdminDashboardResponse.FeedbackItem;
import com.cropcoders.procurement.dto.AdminDashboardResponse.Payments;
import com.cropcoders.procurement.entity.VisitFeedback;
import com.cropcoders.procurement.enums.AppIssue;
import com.cropcoders.procurement.enums.CentreIssue;

/**
 * Numbers for the admin dashboard, counted by the database with plain SQL
 * (COUNT ... FILTER, SUM, AVG) instead of loading every booking into Java.
 * Every query takes a centre id: null counts all centres (super admin), otherwise only that centre.
 */
@Repository
public class DashboardRepository {

    private static final String CENTRES_TODAY = """
            SELECT c.id, c.code, c.name, c.active, c.counters,
                   count(b.id) FILTER (WHERE b.status <> 'CANCELLED')                     AS booked,
                   count(b.id) FILTER (WHERE b.status = 'BOOKED')                         AS expected,
                   count(b.id) FILTER (WHERE b.status = 'CHECKED_IN')                     AS waiting,
                   count(b.id) FILTER (WHERE b.status = 'CALLED')                         AS at_counter,
                   count(b.id) FILTER (WHERE b.status IN ('COMPLETED', 'REJECTED'))       AS served,
                   count(b.id) FILTER (WHERE b.status = 'NO_SHOW')                        AS no_show,
                   coalesce(sum(p.weight_qtl) FILTER (WHERE p.accepted), 0)               AS quantity,
                   avg(extract(epoch FROM b.called_at - b.checked_in_at) / 60)
                       FILTER (WHERE b.called_at IS NOT NULL AND b.checked_in_at IS NOT NULL) AS avg_wait,
                   (SELECT coalesce(sum(s.capacity), 0) FROM slots s
                     WHERE s.centre_id = c.id AND s.slot_date = ?)                        AS capacity,
                   (SELECT round(avg(f.centre_rating), 1) FROM visit_feedback f
                     WHERE f.centre_id = c.id)                                            AS rating,
                   (SELECT count(*) FROM visit_feedback f WHERE f.centre_id = c.id)       AS reviews
            FROM centres c
            LEFT JOIN bookings b ON b.centre_id = c.id AND b.booking_date = ?
            LEFT JOIN procurements p ON p.booking_id = b.id
            WHERE (CAST(? AS bigint) IS NULL OR c.id = CAST(? AS bigint))
            GROUP BY c.id
            ORDER BY c.name""";

    private static final String TODAY_TOTALS = """
            SELECT count(*) FILTER (WHERE b.checked_in_at IS NOT NULL)                   AS arrived,
                   count(*) FILTER (WHERE b.status = 'REJECTED')                          AS rejected,
                   count(*) FILTER (WHERE b.status = 'CANCELLED')                         AS cancelled,
                   coalesce(sum(p.amount) FILTER (WHERE p.accepted), 0)                   AS value,
                   avg(extract(epoch FROM b.called_at - b.checked_in_at) / 60)
                       FILTER (WHERE b.called_at IS NOT NULL AND b.checked_in_at IS NOT NULL) AS avg_wait
            FROM bookings b
            LEFT JOIN procurements p ON p.booking_id = b.id
            WHERE b.booking_date = ? AND (CAST(? AS bigint) IS NULL OR b.centre_id = CAST(? AS bigint))""";

    private static final String PAYMENTS = """
            SELECT count(*) FILTER (WHERE pay.status = 'PENDING')                                  AS pending,
                   coalesce(sum(pay.amount_paise) FILTER (WHERE pay.status = 'PENDING'), 0)        AS pending_paise,
                   count(*) FILTER (WHERE pay.status = 'PAID' AND pay.paid_at >= ?)                AS paid_today,
                   coalesce(sum(pay.amount_paise) FILTER (WHERE pay.status = 'PAID' AND pay.paid_at >= ?), 0) AS paid_today_paise,
                   coalesce(sum(pay.amount_paise) FILTER (WHERE pay.status = 'PAID'), 0)           AS paid_total_paise
            FROM payments pay
            JOIN procurements pr ON pr.id = pay.procurement_id
            JOIN bookings b ON b.id = pr.booking_id
            WHERE (CAST(? AS bigint) IS NULL OR b.centre_id = CAST(? AS bigint))""";

    // generate_series makes one row per day, so days without bookings still show as 0.
    private static final String LAST_DAYS = """
            SELECT CAST(d.day AS date) AS day,
                   count(b.id) FILTER (WHERE b.status <> 'CANCELLED')               AS booked,
                   count(b.id) FILTER (WHERE b.status IN ('COMPLETED', 'REJECTED')) AS served,
                   count(b.id) FILTER (WHERE b.status = 'NO_SHOW')                  AS no_show,
                   coalesce(sum(p.weight_qtl) FILTER (WHERE p.accepted), 0)         AS quantity,
                   coalesce(sum(p.amount) FILTER (WHERE p.accepted), 0)             AS value
            FROM generate_series(CAST(? AS date), CAST(? AS date), interval '1 day') AS d(day)
            LEFT JOIN bookings b ON b.booking_date = CAST(d.day AS date) AND (CAST(? AS bigint) IS NULL OR b.centre_id = CAST(? AS bigint))
            LEFT JOIN procurements p ON p.booking_id = b.id
            GROUP BY d.day
            ORDER BY d.day""";

    private static final String CROPS = """
            SELECT cr.name_en AS crop, count(p.id) AS farmers,
                   coalesce(sum(p.weight_qtl), 0) AS quantity, coalesce(sum(p.amount), 0) AS value
            FROM crops cr
            LEFT JOIN bookings b ON b.crop_id = cr.id AND b.booking_date BETWEEN ? AND ? AND (CAST(? AS bigint) IS NULL OR b.centre_id = CAST(? AS bigint))
            LEFT JOIN procurements p ON p.booking_id = b.id AND p.accepted
            GROUP BY cr.id, cr.name_en
            ORDER BY quantity DESC, cr.name_en""";

    // Farmers: registered on the whole site, and those whose crop was weighed (accepted or rejected) at the
    // admin's centre(s), all time. count(DISTINCT ...) counts a farmer who came 3 times only once.
    private static final String FARMERS = """
            SELECT (SELECT count(*) FROM users WHERE role = 'FARMER')                     AS registered,
                   (SELECT count(*) FROM users WHERE role = 'FARMER' AND created_at >= ?) AS new_this_week,
                   count(DISTINCT b.farmer_id)                                            AS brought_crops,
                   count(DISTINCT b.farmer_id) FILTER (WHERE b.status = 'COMPLETED')      AS accepted,
                   count(*)                                                               AS visits
            FROM bookings b
            WHERE b.status IN ('COMPLETED', 'REJECTED')
              AND (CAST(? AS bigint) IS NULL OR b.centre_id = CAST(? AS bigint))""";

    private static final String FEEDBACK_TOTALS = """
            SELECT count(*)                       AS reviews,
                   round(avg(f.centre_rating), 1) AS avg_centre,
                   round(avg(f.app_rating), 1)    AS avg_app
            FROM visit_feedback f
            WHERE (CAST(? AS bigint) IS NULL OR f.centre_id = CAST(? AS bigint))""";

    // The codes are stored comma-separated ("WAITING,FACILITIES"): string_to_array + unnest make one row per
    // chosen code, so the database can count how often each one was chosen.
    private static final String ISSUE_COUNTS = """
            SELECT 'CENTRE' AS kind, i.code, count(*) AS times
            FROM visit_feedback f CROSS JOIN LATERAL unnest(string_to_array(f.centre_issues, ',')) AS i(code)
            WHERE (CAST(? AS bigint) IS NULL OR f.centre_id = CAST(? AS bigint))
            GROUP BY i.code
            UNION ALL
            SELECT 'APP' AS kind, i.code, count(*) AS times
            FROM visit_feedback f CROSS JOIN LATERAL unnest(string_to_array(f.app_issues, ',')) AS i(code)
            WHERE (CAST(? AS bigint) IS NULL OR f.centre_id = CAST(? AS bigint))
            GROUP BY i.code""";

    private static final String RECENT_FEEDBACK = """
            SELECT f.id, u.name AS farmer_name, c.name AS centre_name, b.token, b.booking_date,
                   f.centre_rating, f.app_rating, f.centre_issues, f.app_issues, f.centre_comment, f.app_comment,
                   f.created_at
            FROM visit_feedback f
            JOIN bookings b ON b.id = f.booking_id
            JOIN users u ON u.id = f.farmer_id
            JOIN centres c ON c.id = f.centre_id
            WHERE (CAST(? AS bigint) IS NULL OR f.centre_id = CAST(? AS bigint))
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT ?""";

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CentreToday> centresToday(LocalDate day, Long centreId) {
        return jdbc.query(CENTRES_TODAY, (rs, i) -> new CentreToday(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getBoolean("active"),
                rs.getInt("counters"), rs.getInt("booked"), rs.getInt("expected"), rs.getInt("waiting"),
                rs.getInt("at_counter"), rs.getInt("served"), rs.getInt("no_show"), rs.getBigDecimal("quantity"),
                minutes(rs, "avg_wait"), rs.getInt("capacity"), rs.getBigDecimal("rating"), rs.getInt("reviews")),
                day, day, centreId, centreId);
    }

    /** arrived, rejected, cancelled, value (rupees), average wait (minutes or null). */
    public TodayTotals todayTotals(LocalDate day, Long centreId) {
        return jdbc.queryForObject(TODAY_TOTALS, (rs, i) -> new TodayTotals(rs.getInt("arrived"),
                rs.getInt("rejected"), rs.getInt("cancelled"), rs.getBigDecimal("value"), minutes(rs, "avg_wait")),
                day, centreId, centreId);
    }

    public record TodayTotals(int arrived, int rejected, int cancelled, BigDecimal value, Integer avgWaitMinutes) {
    }

    public Payments payments(LocalDate day, Long centreId) {
        LocalDateTime startOfDay = day.atStartOfDay();
        return jdbc.queryForObject(PAYMENTS, (rs, i) -> new Payments(
                rs.getInt("pending"), rupees(rs.getLong("pending_paise")),
                rs.getInt("paid_today"), rupees(rs.getLong("paid_today_paise")),
                rupees(rs.getLong("paid_total_paise"))), startOfDay, startOfDay, centreId, centreId);
    }

    public List<DayTotal> dayTotals(LocalDate from, LocalDate to, Long centreId) {
        return jdbc.query(LAST_DAYS, (rs, i) -> new DayTotal(rs.getObject("day", LocalDate.class),
                rs.getInt("booked"), rs.getInt("served"), rs.getInt("no_show"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("value")), from, to, centreId, centreId);
    }

    public List<CropTotal> cropTotals(LocalDate from, LocalDate to, Long centreId) {
        return jdbc.query(CROPS, (rs, i) -> new CropTotal(rs.getString("crop"), rs.getInt("farmers"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("value")), from, to, centreId, centreId);
    }

    /** newSince = the start of the first day of "this week" (6 days ago, so with today it makes 7 days). */
    public Farmers farmers(LocalDateTime newSince, Long centreId) {
        return jdbc.queryForObject(FARMERS, (rs, i) -> new Farmers(rs.getInt("registered"),
                rs.getInt("new_this_week"), rs.getInt("brought_crops"), rs.getInt("accepted"), rs.getInt("visits")),
                newSince, centreId, centreId);
    }

    /** Number of reviews and the average stars (null when there are no reviews). */
    public FeedbackTotals feedbackTotals(Long centreId) {
        return jdbc.queryForObject(FEEDBACK_TOTALS, (rs, i) -> new FeedbackTotals(rs.getInt("reviews"),
                rs.getBigDecimal("avg_centre"), rs.getBigDecimal("avg_app")), centreId, centreId);
    }

    public record FeedbackTotals(int count, BigDecimal avgCentreRating, BigDecimal avgAppRating) {
    }

    /** How often each improvement code was chosen: kind ("CENTRE" or "APP") -> code -> times. Unchosen codes are missing. */
    public Map<String, Map<String, Integer>> issueCounts(Long centreId) {
        Map<String, Map<String, Integer>> counts = new HashMap<>();
        jdbc.query(ISSUE_COUNTS, rs -> {
            counts.computeIfAbsent(rs.getString("kind"), kind -> new HashMap<>())
                    .put(rs.getString("code"), rs.getInt("times"));
        }, centreId, centreId, centreId, centreId);
        return counts;
    }

    /** The newest reviews first, with the farmer's name, the centre and the token of the visit. */
    public List<FeedbackItem> recentFeedback(Long centreId, int limit) {
        return jdbc.query(RECENT_FEEDBACK, (rs, i) -> new FeedbackItem(
                rs.getLong("id"), rs.getString("farmer_name"), rs.getString("centre_name"), rs.getString("token"),
                rs.getObject("booking_date", LocalDate.class), rs.getInt("centre_rating"), rs.getInt("app_rating"),
                VisitFeedback.splitCodes(rs.getString("centre_issues"), CentreIssue.class),
                VisitFeedback.splitCodes(rs.getString("app_issues"), AppIssue.class),
                rs.getString("centre_comment"), rs.getString("app_comment"),
                rs.getObject("created_at", LocalDateTime.class)), centreId, centreId, limit);
    }

    private static Integer minutes(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : (int) Math.round(value.doubleValue());
    }

    private static BigDecimal rupees(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }
}
