package com.cropcoders.procurement.security;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limits per IP address on the login endpoints, the same limits Nginx enforced on the EC2 server
 * (deploy/nginx-sfps.conf). On Render there is no Nginx, so the app does it itself:
 *   sending an OTP email:                6 a minute, plus a burst of 12 at once
 *   checking an OTP or a staff password: 20 a minute, plus a burst of 20 at once
 * Over the limit: 429 with code RATE_LIMITED (the contract with the pages).
 * The address is the visitor's real one: server.forward-headers-strategy=native takes it from the proxy's
 * X-Forwarded-For header, trusting that header only from the proxy itself.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private record Limit(String zone, double perMinute, int burst) {
    }

    private static final Limit OTP = new Limit("otp", 6, 12);
    private static final Limit LOGIN = new Limit("login", 20, 20);
    private static final Map<String, Limit> LIMITED = Map.of(
            "/api/auth/otp/send", OTP,
            "/api/auth/otp/verify", LOGIN,
            "/api/auth/login", LOGIN);
    /** Above this many addresses, buckets that have refilled completely are dropped (memory stays small). */
    private static final int MAX_TRACKED = 10_000;

    /** Token bucket: holds up to burst tokens, refills perMinute tokens a minute, one request takes one. */
    private static final class Bucket {
        private double tokens;
        private long updatedNanos;

        Bucket(int burst, long now) {
            this.tokens = burst;
            this.updatedNanos = now;
        }

        synchronized boolean take(Limit limit, long now) {
            refill(limit, now);
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }

        synchronized boolean isFull(Limit limit, long now) {
            refill(limit, now);
            return tokens >= limit.burst();
        }

        private void refill(Limit limit, long now) {
            tokens = Math.min(limit.burst(), tokens + (now - updatedNanos) / 60e9 * limit.perMinute());
            updatedNanos = now;
        }
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Requests from this machine itself (local development, the tests) are not limited. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        boolean loopback = "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
        return loopback || !"POST".equals(request.getMethod()) || !LIMITED.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Limit limit = LIMITED.get(request.getRequestURI());
        long now = System.nanoTime();
        if (buckets.size() > MAX_TRACKED) {
            buckets.entrySet().removeIf(e -> e.getValue().isFull(limitOf(e.getKey()), now));
        }
        Bucket bucket = buckets.computeIfAbsent(limit.zone() + "|" + request.getRemoteAddr(), k -> new Bucket(limit.burst(), now));
        if (!bucket.take(limit, now)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"status\":429,\"code\":\"RATE_LIMITED\",\"message\":\"Too many attempts. Please wait a minute and try again.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static Limit limitOf(String key) {
        return key.startsWith(OTP.zone() + "|") ? OTP : LOGIN;
    }
}
