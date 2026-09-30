package com.cropcoders.procurement.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cropcoders.procurement.dto.AdminDashboardResponse.CentreToday;
import com.cropcoders.procurement.dto.AdminDashboardResponse.CropTotal;
import com.cropcoders.procurement.dto.AdminDashboardResponse.DayTotal;
import com.cropcoders.procurement.dto.AdminDashboardResponse.Payments;

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
                     WHERE s.centre_id = c.id AND s.slot_date = ?)                        AS capacity
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

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CentreToday> centresToday(LocalDate day, Long centreId) {
        return jdbc.query(CENTRES_TODAY, (rs, i) -> new CentreToday(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getBoolean("active"),
                rs.getInt("counters"), rs.getInt("booked"), rs.getInt("expected"), rs.getInt("waiting"),
                rs.getInt("at_counter"), rs.getInt("served"), rs.getInt("no_show"), rs.getBigDecimal("quantity"),
                minutes(rs, "avg_wait"), rs.getInt("capacity")), day, day, centreId, centreId);
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

    private static Integer minutes(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : (int) Math.round(value.doubleValue());
    }

    private static BigDecimal rupees(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }
}
