package com.cropcoders.procurement.security;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encrypts farmer details that were saved before their column was encrypted (Aadhaar last 4 digits since
 * V10, account number and UPI ID since V7). EncryptedText reads such plain values, but they should not stay
 * readable in the database. Runs once at every start and finds nothing to do after the first time.
 */
@Component
public class EncryptExistingValues {

    private static final Logger log = LoggerFactory.getLogger(EncryptExistingValues.class);
    private static final List<String> COLUMNS = List.of("aadhaar_last4", "account_number", "upi_id");

    private final JdbcTemplate jdbc;
    private final EncryptedText encrypted;

    public EncryptExistingValues(JdbcTemplate jdbc, EncryptedText encrypted) {
        this.jdbc = jdbc;
        this.encrypted = encrypted;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void encryptPlainValues() {
        for (String column : COLUMNS) {     // fixed names above, never user input
            List<Map<String, Object>> plain = jdbc.queryForList(
                    "SELECT user_id, " + column + " AS value FROM farmer_profiles WHERE " + column
                            + " IS NOT NULL AND " + column + " NOT LIKE 'enc:%' FOR UPDATE");
            for (Map<String, Object> row : plain) {
                jdbc.update("UPDATE farmer_profiles SET " + column + " = ? WHERE user_id = ?",
                        encrypted.convertToDatabaseColumn((String) row.get("value")), row.get("user_id"));
            }
            if (!plain.isEmpty()) {
                log.info("Encrypted {} saved value(s) in farmer_profiles.{}", plain.size(), column);
            }
        }
    }
}
