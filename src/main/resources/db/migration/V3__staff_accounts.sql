-- =====================================================================
-- Demo staff accounts (passwords are stored as BCrypt hashes).
--
--   Username        Password       Role       Centre
--   admin           Admin@123      ADMIN      -
--   operator.tnj    Operator@123   OPERATOR   Thanjavur
--   operator.tvr    Operator@123   OPERATOR   Tiruvarur
--   operator.ngt    Operator@123   OPERATOR   Nagapattinam
--
-- These are demo passwords. Change them on the deployed site.
-- =====================================================================

INSERT INTO users (name, role, username, password_hash, centre_id, created_at)
VALUES ('District Admin', 'ADMIN', 'admin',
        '$2a$10$wzGNytuLoD/ruBgouCmBPeAQzJjZycNRm8Q4YtNnwmfA4Pm4pxAn6', NULL, LOCALTIMESTAMP),
       ('Thanjavur Operator', 'OPERATOR', 'operator.tnj',
        '$2a$10$8gthxB/E4m3YHHgvb9.Bo.Q7g6plygmgBimrulj30cQ/mCEEBikRO', (SELECT id FROM centres WHERE code = 'TNJ'), LOCALTIMESTAMP),
       ('Tiruvarur Operator', 'OPERATOR', 'operator.tvr',
        '$2a$10$8gthxB/E4m3YHHgvb9.Bo.Q7g6plygmgBimrulj30cQ/mCEEBikRO', (SELECT id FROM centres WHERE code = 'TVR'), LOCALTIMESTAMP),
       ('Nagapattinam Operator', 'OPERATOR', 'operator.ngt',
        '$2a$10$8gthxB/E4m3YHHgvb9.Bo.Q7g6plygmgBimrulj30cQ/mCEEBikRO', (SELECT id FROM centres WHERE code = 'NGT'), LOCALTIMESTAMP);
