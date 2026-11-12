-- =====================================================================
-- The last 4 digits of Aadhaar are stored encrypted too (AES-256-GCM, see EncryptedText.java), like
-- the bank account number and UPI ID (V7). The column holds "enc:..." text: wider, and the digits-only
-- check no longer applies; the app checks the 4 digits before it encrypts them (RegisterRequest).
-- Values saved before this are encrypted by EncryptExistingValues when the app starts.
-- =====================================================================

ALTER TABLE farmer_profiles DROP CONSTRAINT IF EXISTS farmer_profiles_aadhaar_last4_check;
ALTER TABLE farmer_profiles ALTER COLUMN aadhaar_last4 TYPE VARCHAR(100);
