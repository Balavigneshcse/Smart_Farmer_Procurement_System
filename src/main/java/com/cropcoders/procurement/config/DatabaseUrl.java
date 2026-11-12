package com.cropcoders.procurement.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Neon gives the database as one DATABASE_URL like postgresql://user:password@host/db?sslmode=require,
 * which the JDBC driver cannot use as it is. This turns it into the three values Spring needs.
 * Without DATABASE_URL (on the PC) nothing changes and application.yml's DB_URL / DB_USERNAME / DB_PASSWORD apply.
 */
public record DatabaseUrl(String jdbcUrl, String username, String password) {

    public static Optional<DatabaseUrl> fromEnvironment() {
        String raw = System.getenv("DATABASE_URL");
        return (raw == null || raw.isBlank()) ? Optional.empty() : Optional.of(parse(raw.trim()));
    }

    public static DatabaseUrl parse(String value) {
        if (value.startsWith("jdbc:postgresql://")) {
            return new DatabaseUrl(value, null, null);
        }
        if (!value.startsWith("postgres://") && !value.startsWith("postgresql://")) {
            throw new IllegalArgumentException("DATABASE_URL must start with postgres:// or postgresql://");
        }
        URI uri = URI.create(value);
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String database = decode(uri.getRawPath().substring(1));
        String user = null;
        String password = null;
        String info = uri.getRawUserInfo();
        if (info != null) {
            int colon = info.indexOf(':');
            user = decode(colon >= 0 ? info.substring(0, colon) : info);
            password = colon >= 0 ? decode(info.substring(colon + 1)) : null;
        }
        String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + "/" + database;
        // Keep only sslmode; other libpq options (channel_binding ...) confuse the JDBC driver.
        String query = uri.getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                if (pair.startsWith("sslmode=")) {
                    jdbc += "?" + secure(pair);
                }
            }
        }
        return new DatabaseUrl(jdbc, user, password);
    }

    /**
     * sslmode=require encrypts but accepts any certificate. verify-full also checks that the certificate
     * belongs to this host and is signed by a trusted authority (Java's own list), so nobody in between can
     * pose as the database.
     */
    private static String secure(String sslmode) {
        return sslmode.equals("sslmode=require")
                ? "sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory"
                : sslmode;
    }

    /** System properties win over application.yml, so these replace the local database settings. */
    public void applyAsSystemProperties() {
        System.setProperty("spring.datasource.url", jdbcUrl);
        if (username != null) {
            System.setProperty("spring.datasource.username", username);
        }
        if (password != null) {
            System.setProperty("spring.datasource.password", password);
        }
    }

    private static String decode(String s) {
        // '+' in a password must stay '+'
        return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "DatabaseUrl[" + jdbcUrl + ", user=" + username + ", password=***]";
    }
}
