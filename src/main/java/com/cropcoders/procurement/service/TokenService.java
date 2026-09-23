package com.cropcoders.procurement.service;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.cropcoders.procurement.entity.Centre;

/** Hands out token numbers per centre per day: TNJ-001, TNJ-002, ... */
@Service
public class TokenService {

    // One atomic statement: creates today's counter at 1, or adds 1 to it, and returns the new number.
    // Two bookings at the same moment always get different numbers.
    private static final String NEXT_NUMBER = """
            INSERT INTO token_counters (centre_id, day, last_no) VALUES (?, ?, 1)
            ON CONFLICT (centre_id, day) DO UPDATE SET last_no = token_counters.last_no + 1
            RETURNING last_no""";

    private final JdbcTemplate jdbc;

    public TokenService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String nextToken(Centre centre, LocalDate day) {
        Integer number = jdbc.queryForObject(NEXT_NUMBER, Integer.class, centre.getId(), day);
        return "%s-%03d".formatted(centre.getCode(), number);
    }
}
