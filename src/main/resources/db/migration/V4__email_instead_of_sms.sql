-- =====================================================================
-- Farmers log in with email + OTP, and notifications go by email instead of SMS.
-- =====================================================================

-- Farmers: email is the login (stored in lower case, unique). The mobile number stays
-- as contact information for the centre, and is no longer unique (family members may share a phone).
ALTER TABLE users ADD COLUMN email VARCHAR(254);
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
ALTER TABLE users ADD CONSTRAINT chk_users_email CHECK (email IS NULL OR email = lower(email));
ALTER TABLE users DROP CONSTRAINT users_mobile_key;

ALTER TABLE users DROP CONSTRAINT chk_user_login;
ALTER TABLE users ADD CONSTRAINT chk_user_login CHECK (
    (role = 'FARMER' AND email IS NOT NULL AND mobile IS NOT NULL)
    OR (role <> 'FARMER' AND username IS NOT NULL AND password_hash IS NOT NULL));

-- One-time passwords are now sent to an email address.
DELETE FROM otp_codes;                                   -- old mobile OTPs have all expired anyway
ALTER TABLE otp_codes RENAME COLUMN mobile TO email;
ALTER TABLE otp_codes ALTER COLUMN email TYPE VARCHAR(254);
ALTER INDEX idx_otp_mobile RENAME TO idx_otp_email;

-- Notifications: sent by email (key events) and always shown in the app.
-- PENDING = saved, email being sent in the background right after the change is committed.
ALTER TABLE notifications RENAME COLUMN mobile TO email;
ALTER TABLE notifications ALTER COLUMN email TYPE VARCHAR(254);
ALTER TABLE notifications RENAME COLUMN sms_status TO email_status;
ALTER TABLE notifications DROP CONSTRAINT notifications_sms_status_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_email_status_check
    CHECK (email_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED', 'IN_APP_ONLY'));
ALTER TABLE notifications ADD COLUMN subject VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE notifications ALTER COLUMN subject DROP DEFAULT;
DROP INDEX idx_notifications_sms_day;
CREATE INDEX idx_notifications_email_day ON notifications (created_at) WHERE email_status = 'SENT';
