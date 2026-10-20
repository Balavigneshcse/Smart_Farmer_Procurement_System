package com.cropcoders.procurement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EncryptedTextTest {

    private static final String LOCAL = "http://localhost:8080";

    private final EncryptedText converter = new EncryptedText("a-test-key-of-enough-length", LOCAL);

    @Test
    void savedValueIsUnreadableAndReadsBackTheSame() {
        String stored = converter.convertToDatabaseColumn("123456789012");

        assertThat(stored).startsWith("enc:").doesNotContain("123456789012");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("123456789012");
    }

    @Test
    void sameValueIsStoredDifferentlyEachTime() {
        assertThat(converter.convertToDatabaseColumn("ravi@okaxis"))
                .isNotEqualTo(converter.convertToDatabaseColumn("ravi@okaxis"));
    }

    @Test
    void anotherKeyCannotReadIt() {
        String stored = converter.convertToDatabaseColumn("ravi@okaxis");
        EncryptedText other = new EncryptedText("a-different-key-of-enough-length", LOCAL);

        assertThatThrownBy(() -> other.convertToEntityAttribute(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void developmentKeyIsRefusedOnAServer() {
        assertThatThrownBy(() -> new EncryptedText("local-dev-payout-key-change-me-0123456789", "https://sfps.example.in"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYOUT_ENCRYPTION_KEY");
    }

    @Test
    void emptyStaysEmpty() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
