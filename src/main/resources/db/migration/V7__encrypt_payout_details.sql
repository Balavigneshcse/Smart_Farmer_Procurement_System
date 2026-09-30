-- =====================================================================
-- The bank account number and UPI ID are stored encrypted (AES-256-GCM, see EncryptedText.java),
-- so the columns hold "enc:..." text: wider, and the digits-only check no longer applies.
-- The format is checked by the app before it encrypts (PayoutRequest).
-- =====================================================================

ALTER TABLE farmer_profiles DROP CONSTRAINT IF EXISTS farmer_profiles_account_number_check;
ALTER TABLE farmer_profiles ALTER COLUMN account_number TYPE VARCHAR(255);
ALTER TABLE farmer_profiles ALTER COLUMN upi_id TYPE VARCHAR(400);
