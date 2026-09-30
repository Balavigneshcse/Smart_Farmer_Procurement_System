-- =====================================================================
-- Roles: super admin -> centre admin -> staff.
--
--   SUPER_ADMIN  adds centres and centre admins, sets crop rates, sees every centre.
--                Created when the app starts, from SUPERADMIN_USERNAME / SUPERADMIN_PASSWORD (.env).
--   ADMIN        belongs to one centre: its dashboard, payments, settings and staff.
--   OPERATOR     counter staff of one centre (the operator desk).
--
-- The old demo accounts (admin, operator.*) are replaced by one admin and one staff
-- account per centre. Their starting passwords are random; only the hashes are here.
-- =====================================================================

ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('FARMER', 'OPERATOR', 'ADMIN', 'SUPER_ADMIN'));

-- Remove the old all-centres admin (history rows keep working without it).
UPDATE booking_events SET created_by = NULL
 WHERE created_by IN (SELECT id FROM users WHERE role = 'ADMIN');
UPDATE procurements SET recorded_by = (SELECT min(id) FROM users WHERE role = 'OPERATOR')
 WHERE recorded_by IN (SELECT id FROM users WHERE role = 'ADMIN');
DELETE FROM users WHERE role = 'ADMIN';

-- The three operator rows become the new staff accounts (weighments already recorded point to them).
UPDATE users SET name = 'Thanjavur Counter Staff', username = 'staff.tnj',
       password_hash = '$2a$10$y/MpKhvrH2J8CeJRaGpn8.bh6.qJ2AthoVHk7medqHSBjHZWO5sVK', active = TRUE
 WHERE role = 'OPERATOR' AND username = 'operator.tnj';
UPDATE users SET name = 'Tiruvarur Counter Staff', username = 'staff.tvr',
       password_hash = '$2a$10$YsO39xRij87CEWUtlT9scuuZQka7M2vgl5X3XkgURWdU0GekdtwjS', active = TRUE
 WHERE role = 'OPERATOR' AND username = 'operator.tvr';
UPDATE users SET name = 'Nagapattinam Counter Staff', username = 'staff.ngt',
       password_hash = '$2a$10$cOMpVZ1o8sokwtG8.nSpruavGLQgmjFlYei2PLl5zt4bCjot4gSC.', active = TRUE
 WHERE role = 'OPERATOR' AND username = 'operator.ngt';

-- One admin per centre.
INSERT INTO users (name, role, username, password_hash, centre_id, created_at)
VALUES ('Thanjavur Centre Admin', 'ADMIN', 'admin.tnj',
        '$2a$10$5FZV5FE8VEd6vNgLGtTeAuERnul0yAR93sTwzYvj.YKIokU.aLNz6', (SELECT id FROM centres WHERE code = 'TNJ'), LOCALTIMESTAMP),
       ('Tiruvarur Centre Admin', 'ADMIN', 'admin.tvr',
        '$2a$10$WzHEdkv1pIn.qulGWeJxFefi3stAMPI3RpTkAYCWan5Zl9..TKV0i', (SELECT id FROM centres WHERE code = 'TVR'), LOCALTIMESTAMP),
       ('Nagapattinam Centre Admin', 'ADMIN', 'admin.ngt',
        '$2a$10$aaXmOqLhDBX45RjNpMBLV.GePGRGGIuk6udImSZKltCTlgbofzcuW', (SELECT id FROM centres WHERE code = 'NGT'), LOCALTIMESTAMP);

-- Admins and staff always belong to a centre.
ALTER TABLE users DROP CONSTRAINT chk_operator_centre;
ALTER TABLE users ADD CONSTRAINT chk_staff_centre
    CHECK (role NOT IN ('OPERATOR', 'ADMIN') OR centre_id IS NOT NULL);
CREATE INDEX idx_users_role_centre ON users (role, centre_id);
