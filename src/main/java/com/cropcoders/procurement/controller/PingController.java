package com.cropcoders.procurement.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Health check that never touches the database.
 * Render uses it to see the app is up, without waking the Neon database.
 */
@RestController
public class PingController {

    /**
     * yourIp is the caller's own address as the app sees it (behind Render's proxy it must be the visitor's
     * public address, not the proxy's): the per-IP login limits depend on it. It shows callers only themselves.
     */
    @GetMapping("/api/ping")
    public Map<String, Object> ping(HttpServletRequest request) {
        return Map.of("status", "ok", "time", LocalDateTime.now().toString(), "yourIp", request.getRemoteAddr());
    }
}
