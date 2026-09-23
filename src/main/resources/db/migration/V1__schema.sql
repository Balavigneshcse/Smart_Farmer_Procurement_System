-- =====================================================================
-- Smart Farmer Procurement System - database schema (V1)
-- Flyway runs this file once. Never edit it after it has been applied;
-- add a new V<n>__description.sql file instead.
-- All timestamps are IST wall-clock time (the app runs in Asia/Kolkata).
-- =====================================================================

-- Procurement centres (Direct Purchase Centres)
CREATE TABLE centres (
    id              BIGSERIAL    PRIMARY KEY,
    code            VARCHAR(5)   NOT NULL UNIQUE,          -- token prefix, e.g. TNJ-017
    name            VARCHAR(100) NOT NULL,
    district        VARCHAR(60)  NOT NULL,
    address         VARCHAR(255) NOT NULL,
    open_time       TIME         NOT NULL,
    close_time      TIME         NOT NULL,
    slot_minutes    INTEGER      NOT NULL CHECK (slot_minutes BETWEEN 10 AND 120),
    counters        INTEGER      NOT NULL CHECK (counters BETWEEN 1 AND 20),
    avg_service_min INTEGER      NOT NULL CHECK (avg_service_min BETWEEN 1 AND 60),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_centre_hours CHECK (close_time > open_time)
);

-- Crops that are procured, with the rate paid per quintal
CREATE TABLE crops (
    id               BIGSERIAL     PRIMARY KEY,
    name_en          VARCHAR(60)   NOT NULL UNIQUE,
    name_ta          VARCHAR(60)   NOT NULL,
    name_hi          VARCHAR(60)   NOT NULL,
    rate_per_qtl     NUMERIC(10,2) NOT NULL CHECK (rate_per_qtl > 0),
    max_moisture_pct NUMERIC(4,1)  NOT NULL CHECK (max_moisture_pct BETWEEN 0 AND 100),
    active           BOOLEAN       NOT NULL DEFAULT TRUE
);

-- Everyone who can log in.
-- Farmers log in with mobile + OTP; operators and admins with username + password.
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('FARMER', 'OPERATOR', 'ADMIN')),
    mobile        VARCHAR(10)  UNIQUE CHECK (mobile ~ '^[6-9][0-9]{9}$'),
    username      VARCHAR(50)  UNIQUE,
    password_hash VARCHAR(100),
    centre_id     BIGINT       REFERENCES centres (id),   -- the centre an operator works at
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT chk_user_login CHECK (
        (role = 'FARMER' AND mobile IS NOT NULL)
        OR (role <> 'FARMER' AND username IS NOT NULL AND password_hash IS NOT NULL)),
    CONSTRAINT chk_operator_centre CHECK (role <> 'OPERATOR' OR centre_id IS NOT NULL)
);

-- Details a farmer gives at registration (language drives their pages and SMS)
CREATE TABLE farmer_profiles (
    user_id       BIGINT       PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    address       VARCHAR(255) NOT NULL,
    district      VARCHAR(60)  NOT NULL,
    aadhaar_last4 VARCHAR(4)   NOT NULL CHECK (aadhaar_last4 ~ '^[0-9]{4}$'),
    language      VARCHAR(2)   NOT NULL DEFAULT 'en' CHECK (language IN ('en', 'ta', 'hi')),
    updated_at    TIMESTAMP    NOT NULL
);

-- One-time passwords for farmer login (only a hash of the code is stored)
CREATE TABLE otp_codes (
    id         BIGSERIAL    PRIMARY KEY,
    mobile     VARCHAR(10)  NOT NULL,
    code_hash  VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    attempts   INTEGER      NOT NULL DEFAULT 0,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL
);
CREATE INDEX idx_otp_mobile ON otp_codes (mobile, created_at DESC);

-- Bookable time slots (generated automatically for the next 7 days)
CREATE TABLE slots (
    id         BIGSERIAL PRIMARY KEY,
    centre_id  BIGINT    NOT NULL REFERENCES centres (id),
    slot_date  DATE      NOT NULL,
    start_time TIME      NOT NULL,
    end_time   TIME      NOT NULL,
    capacity   INTEGER   NOT NULL CHECK (capacity >= 0),
    booked     INTEGER   NOT NULL DEFAULT 0,
    CONSTRAINT uq_slot UNIQUE (centre_id, slot_date, start_time),
    CONSTRAINT chk_slot_booked CHECK (booked >= 0 AND booked <= capacity)
);

-- Running token number per centre per day (TNJ-001, TNJ-002, ...)
CREATE TABLE token_counters (
    centre_id BIGINT  NOT NULL REFERENCES centres (id),
    day       DATE    NOT NULL,
    last_no   INTEGER NOT NULL,
    PRIMARY KEY (centre_id, day)
);

-- One farmer visit: a crop brought to a centre in a booked slot
CREATE TABLE bookings (
    id              BIGSERIAL    PRIMARY KEY,
    token           VARCHAR(12)  NOT NULL,
    farmer_id       BIGINT       NOT NULL REFERENCES users (id),
    slot_id         BIGINT       NOT NULL REFERENCES slots (id),
    centre_id       BIGINT       NOT NULL REFERENCES centres (id),
    booking_date    DATE         NOT NULL,
    crop_id         BIGINT       NOT NULL REFERENCES crops (id),
    est_qty_qtl     NUMERIC(8,2) NOT NULL CHECK (est_qty_qtl > 0),
    status          VARCHAR(20)  NOT NULL CHECK (status IN
                        ('BOOKED', 'CHECKED_IN', 'CALLED', 'COMPLETED', 'REJECTED', 'CANCELLED', 'NO_SHOW')),
    queue_priority  TIMESTAMP,              -- set at check-in; decides queue order
    checked_in_at   TIMESTAMP,
    called_at       TIMESTAMP,
    counter_no      INTEGER,
    completed_at    TIMESTAMP,
    reminder_sent   BOOLEAN      NOT NULL DEFAULT FALSE,
    turn_alert_sent BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uq_token_per_day UNIQUE (centre_id, booking_date, token)
);
-- A farmer can hold only one active booking per day
CREATE UNIQUE INDEX uq_active_booking ON bookings (farmer_id, booking_date)
    WHERE status IN ('BOOKED', 'CHECKED_IN', 'CALLED');
CREATE INDEX idx_bookings_queue ON bookings (centre_id, booking_date, status);
CREATE INDEX idx_bookings_farmer ON bookings (farmer_id, created_at DESC);

-- Everything that happened to a booking, in order (the farmer's tracking timeline)
CREATE TABLE booking_events (
    id         BIGSERIAL    PRIMARY KEY,
    booking_id BIGINT       NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    event      VARCHAR(30)  NOT NULL,
    note       VARCHAR(255),
    created_by BIGINT       REFERENCES users (id),
    created_at TIMESTAMP    NOT NULL
);
CREATE INDEX idx_booking_events_booking ON booking_events (booking_id, created_at);

-- Weighment and quality check result at the counter
CREATE TABLE procurements (
    id           BIGSERIAL     PRIMARY KEY,
    booking_id   BIGINT        NOT NULL UNIQUE REFERENCES bookings (id),
    weight_qtl   NUMERIC(8,2)  NOT NULL CHECK (weight_qtl > 0),
    moisture_pct NUMERIC(4,1)  NOT NULL CHECK (moisture_pct BETWEEN 0 AND 100),
    grade        VARCHAR(1)    NOT NULL CHECK (grade IN ('A', 'B', 'C')),
    rate_per_qtl NUMERIC(10,2) NOT NULL,
    amount       NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    accepted     BOOLEAN       NOT NULL,
    remarks      VARCHAR(255),
    recorded_by  BIGINT        NOT NULL REFERENCES users (id),
    recorded_at  TIMESTAMP     NOT NULL
);

-- Payment to the farmer for an accepted procurement (Razorpay test mode)
CREATE TABLE payments (
    id             BIGSERIAL   PRIMARY KEY,
    procurement_id BIGINT      NOT NULL UNIQUE REFERENCES procurements (id),
    amount_paise   BIGINT      NOT NULL CHECK (amount_paise > 0),
    status         VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED')),
    rzp_order_id   VARCHAR(40),
    rzp_payment_id VARCHAR(40),
    paid_at        TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL
);
CREATE INDEX idx_payments_status ON payments (status);

-- Every message sent to a user. All are shown in-app; SMS only for key events.
CREATE TABLE notifications (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       REFERENCES users (id),     -- null for OTP before registration
    mobile       VARCHAR(10)  NOT NULL,
    booking_id   BIGINT       REFERENCES bookings (id),
    event        VARCHAR(30)  NOT NULL,
    message      VARCHAR(500) NOT NULL,
    sms_status   VARCHAR(20)  NOT NULL CHECK (sms_status IN ('SENT', 'FAILED', 'SKIPPED', 'IN_APP_ONLY')),
    provider_ref VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL,
    read_at      TIMESTAMP
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_sms_day ON notifications (created_at) WHERE sms_status = 'SENT';
