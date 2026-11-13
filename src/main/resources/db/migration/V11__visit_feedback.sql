-- =====================================================================
-- The farmer's review of a visit, asked after the crop is weighed (accepted or rejected):
-- 1 to 5 stars for the centre and for the app, what should improve (tap-to-choose codes) and
-- optional comments. One review per booking (UNIQUE booking_id), and it cannot be changed later.
--
-- Improvement codes are stored comma-separated, e.g. 'WAITING,FACILITIES' (NULL when none chosen):
--   centre_issues: WAITING, STAFF, FACILITIES, WEIGHING, PAYMENT        (enums/CentreIssue.java)
--   app_issues:    BOOKING, LIVE_QUEUE, MESSAGES, LANGUAGE, LOGIN       (enums/AppIssue.java)
-- =====================================================================

CREATE TABLE visit_feedback (
    id             BIGSERIAL    PRIMARY KEY,
    booking_id     BIGINT       NOT NULL UNIQUE REFERENCES bookings (id) ON DELETE CASCADE,
    farmer_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    centre_id      BIGINT       NOT NULL REFERENCES centres (id),     -- copied from the booking for the dashboard
    centre_rating  SMALLINT     NOT NULL CHECK (centre_rating BETWEEN 1 AND 5),
    app_rating     SMALLINT     NOT NULL CHECK (app_rating BETWEEN 1 AND 5),
    centre_issues  VARCHAR(100),
    app_issues     VARCHAR(100),
    centre_comment VARCHAR(500),
    app_comment    VARCHAR(500),
    created_at     TIMESTAMP    NOT NULL
);
-- The admin dashboard reads the reviews of one centre (or all), newest first.
CREATE INDEX idx_visit_feedback_centre ON visit_feedback (centre_id, created_at DESC);

-- The "crop accepted / not accepted" texts now end with a request to review the visit. A long centre name,
-- crop name and rejection reason together could pass 500 characters and cut that request off, so the
-- notification text may now be up to 1000 characters (NotificationService.MAX_MESSAGE). Making a VARCHAR
-- longer only changes the limit: no row is rewritten, and the previous app version still works with it.
ALTER TABLE notifications ALTER COLUMN message TYPE VARCHAR(1000);
