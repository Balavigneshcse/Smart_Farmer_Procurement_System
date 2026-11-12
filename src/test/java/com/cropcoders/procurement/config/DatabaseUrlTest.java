package com.cropcoders.procurement.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseUrlTest {

    @Test
    void neonAddressBecomesJdbcWithACheckedCertificate() {
        DatabaseUrl url = DatabaseUrl.parse(
                "postgresql://farmer:p%40ss+w0rd@ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require");
        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://ep-cool-name-123456.ap-southeast-1.aws.neon.tech:5432/neondb"
                + "?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory");
        assertThat(url.username()).isEqualTo("farmer");
        assertThat(url.password()).isEqualTo("p@ss+w0rd");
        assertThat(url.toString()).doesNotContain("p@ss");
    }

    @Test
    void jdbcAddressIsKeptAndOtherSchemesAreRefused() {
        assertThat(DatabaseUrl.parse("jdbc:postgresql://localhost:5432/db").jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/db");
        assertThatThrownBy(() -> DatabaseUrl.parse("mysql://x@y/z")).isInstanceOf(IllegalArgumentException.class);
    }
}
