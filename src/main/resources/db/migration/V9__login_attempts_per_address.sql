-- =====================================================================
-- Staff login lock per username AND computer (client IP address).
--
-- Before (V8), 5 wrong passwords locked the whole account for 15 minutes, for everyone. The usernames
-- are easy to guess (staff.tnj, admin.tnj, ...), so a stranger could keep the real staff locked out.
-- Now wrong passwords are counted for each (username, IP address) pair: a stranger only locks
-- themselves out, and staff at their own computers can still log in. Usernames that do not exist are
-- counted the same way, so the answers do not show which usernames exist.
--
-- users.failed_logins and users.locked_until (V8) are no longer used, but they are kept for now:
-- if a new version fails to start, deploy/update.sh goes back to the previous version, and that
-- version still expects these columns. They can be dropped in a later migration.
-- =====================================================================

CREATE TABLE login_attempts (
    username     VARCHAR(50) NOT NULL,                       -- as typed, trimmed and in lower case
    ip           VARCHAR(45) NOT NULL,                       -- client IP address (IPv6: up to 45 characters)
    failed       INTEGER     NOT NULL DEFAULT 0 CHECK (failed >= 0),   -- wrong passwords in a row
    locked_until TIMESTAMP,                                  -- refused from this address until then
    updated_at   TIMESTAMP   NOT NULL,
    PRIMARY KEY (username, ip)
);

-- The daily OTP limit counts every OTP created today (all email addresses together).
CREATE INDEX idx_otp_created ON otp_codes (created_at);
