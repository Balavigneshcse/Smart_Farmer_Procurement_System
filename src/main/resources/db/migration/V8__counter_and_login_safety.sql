-- 1. A counter serves one farmer at a time.
-- "Call next" already checks that the counter is free, but two clicks for the same counter at the same
-- moment (two tabs, two staff) could both pass that check. This index makes the database refuse the second one.
-- Any counter that already has two farmers keeps the first one; the others go back to the waiting line.
UPDATE bookings b
SET status = 'CHECKED_IN', counter_no = NULL, called_at = NULL
WHERE b.status = 'CALLED'
  AND EXISTS (SELECT 1 FROM bookings o
              WHERE o.status = 'CALLED' AND o.centre_id = b.centre_id AND o.booking_date = b.booking_date
                AND o.counter_no = b.counter_no AND o.id < b.id);

CREATE UNIQUE INDEX uq_counter_serving ON bookings (centre_id, booking_date, counter_no)
    WHERE status = 'CALLED';

-- 2. Staff logins: wrong passwords in a row, and until when the account is locked after too many.
ALTER TABLE users
    ADD COLUMN failed_logins INTEGER NOT NULL DEFAULT 0 CHECK (failed_logins >= 0),
    ADD COLUMN locked_until  TIMESTAMP;
