package com.cropcoders.procurement.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check that never touches the database.
 * Render uses it to see the app is up, without waking the Neon database.
 */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of("status", "ok", "time", LocalDateTime.now().toString());
    }
}
