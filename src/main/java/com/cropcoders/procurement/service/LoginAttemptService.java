package com.cropcoders.procurement.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.cropcoders.procurement.config.AppProperties;

/**
 * Counts wrong staff passwords per username and client IP address (table login_attempts, V9).
 *
 * After app.staff-login.max-attempts wrong passwords in a row, that username is locked for lock-minutes,
 * but only from that IP address. Someone guessing passwords locks out only themselves, not the real staff.
 * Usernames that do not exist are counted too, so the answers never show which usernames exist.
 */
@Service
public class LoginAttemptService {

    // One atomic statement (like TokenService): creates the row with 1 wrong password, or adds 1 to it.
    // The wrong password that reaches max-attempts sets the lock and starts the count again at 0.
    // Wrong passwords sent at the same moment are all counted: the database adds them one after the other.
    // (So max-attempts should be 2 or more: the very first wrong password only creates the row.)
    private static final String RECORD_FAILURE = """
            INSERT INTO login_attempts AS a (username, ip, failed, updated_at) VALUES (?, ?, 1, ?)
            ON CONFLICT (username, ip) DO UPDATE SET
                failed       = CASE WHEN a.failed + 1 >= ? THEN 0 ELSE a.failed + 1 END,
                locked_until = CASE WHEN a.failed + 1 >= ? THEN ? ELSE a.locked_until END,
                updated_at   = EXCLUDED.updated_at""";

    private static final int MAX_USERNAME = 50;     // column sizes in V9
    private static final int MAX_IP = 45;

    private final JdbcTemplate jdbc;
    private final AppProperties.StaffLogin rules;

    public LoginAttemptService(JdbcTemplate jdbc, AppProperties properties) {
        this.jdbc = jdbc;
        this.rules = properties.staffLogin();
    }

    /** Minutes left (rounded up) if this username is locked for this IP address, otherwise empty. */
    public Optional<Long> minutesLocked(String username, String ip, LocalDateTime now) {
        return jdbc.queryForList("""
                        SELECT locked_until FROM login_attempts
                        WHERE username = ? AND ip = ? AND locked_until > ?""",
                        LocalDateTime.class, key(username), ip(ip), now)
                .stream()
                .findFirst()
                .map(until -> Math.max(1, (Duration.between(now, until).toSeconds() + 59) / 60));
    }

    /** Counts one wrong password; the one that reaches max-attempts locks the username for this address. */
    public void recordFailure(String username, String ip, LocalDateTime now) {
        jdbc.update(RECORD_FAILURE, key(username), ip(ip), now,
                rules.maxAttempts(), rules.maxAttempts(), now.plusMinutes(rules.lockMinutes()));
    }

    /** After the right password: the count for this address starts again. */
    public void clear(String username, String ip) {
        jdbc.update("DELETE FROM login_attempts WHERE username = ? AND ip = ?", key(username), ip(ip));
    }

    /** After an admin sets a new password: lifts the lock for every address. */
    public void clearAll(String username) {
        jdbc.update("DELETE FROM login_attempts WHERE username = ?", key(username));
    }

    /** Housekeeping (AutomationJob): forgets rows untouched for a day that are not locked now. */
    public int deleteOld(LocalDateTime now) {
        return jdbc.update("""
                DELETE FROM login_attempts
                WHERE updated_at < ? AND (locked_until IS NULL OR locked_until < ?)""", now.minusDays(1), now);
    }

    /** "Staff.TNJ " and "staff.tnj" count as the same username. */
    private static String key(String username) {
        return shorten(username.trim().toLowerCase(Locale.ROOT), MAX_USERNAME);
    }

    private static String ip(String ip) {
        return shorten(ip == null ? "unknown" : ip, MAX_IP);
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
