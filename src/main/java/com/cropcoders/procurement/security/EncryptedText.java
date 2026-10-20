package com.cropcoders.procurement.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Keeps a column encrypted in the database (AES-256 in GCM mode). Used for the farmer's bank account
 * number and UPI ID: someone who only gets a copy of the database cannot read them.
 *
 * The key comes from PAYOUT_ENCRYPTION_KEY (.env on the PC, an environment variable on the server).
 * On a server it must be set: the development default from application.yml is refused (SecretGuard).
 * Every value gets its own random 12-byte IV; the column holds "enc:" + Base64(IV + ciphertext + tag).
 * Changing the key makes the saved values unreadable, so keep it safe.
 */
@Component
@Converter
public class EncryptedText implements AttributeConverter<String, String> {

    private static final String PREFIX = "enc:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public EncryptedText(@Value("${app.payout-encryption-key}") String secret,
                         @Value("${app.email.app-url}") String appUrl) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("PAYOUT_ENCRYPTION_KEY must be at least 16 characters long.");
        }
        SecretGuard.requireRealSecret(secret, "PAYOUT_ENCRYPTION_KEY", appUrl);
        try {
            // Any text of any length becomes a 256-bit key.
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] both = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(both);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the value", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored;      // a value saved before encryption was switched on
        }
        try {
            byte[] both = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, both, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(both, IV_BYTES, both.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt a payment detail. Was PAYOUT_ENCRYPTION_KEY changed?", e);
        }
    }
}
