-- =====================================================================
-- Where a farmer is paid: a bank account or a UPI ID (asked at registration).
-- Farmers who registered earlier have no details yet and can add them on their dashboard.
-- =====================================================================

ALTER TABLE farmer_profiles
    ADD COLUMN payout_method  VARCHAR(10) CHECK (payout_method IN ('BANK', 'UPI')),
    ADD COLUMN account_holder VARCHAR(100),
    ADD COLUMN account_number VARCHAR(18) CHECK (account_number ~ '^[0-9]{9,18}$'),
    ADD COLUMN ifsc           VARCHAR(11) CHECK (ifsc ~ '^[A-Z]{4}0[A-Z0-9]{6}$'),
    ADD COLUMN bank_name      VARCHAR(100),
    ADD COLUMN upi_id         VARCHAR(100),
    ADD CONSTRAINT chk_payout_complete CHECK (
        payout_method IS NULL
        OR (payout_method = 'BANK' AND account_holder IS NOT NULL AND account_number IS NOT NULL
            AND ifsc IS NOT NULL AND bank_name IS NOT NULL)
        OR (payout_method = 'UPI' AND upi_id IS NOT NULL));
