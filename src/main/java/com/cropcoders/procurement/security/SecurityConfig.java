package com.cropcoders.procurement.security;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import com.cropcoders.procurement.repository.UserRepository;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Who can call which API.
 * HTML/CSS/JS files are public; each page checks the login in the browser,
 * and the APIs behind it are protected here by role.
 */
@Configuration
public class SecurityConfig {

    /**
     * Content-Security-Policy, sent with every response. The browser then runs only scripts from our own
     * server and the few sites we use, so a script slipped into a page (XSS) cannot run or send data away.
     * The pages therefore have no inline scripts or onclick="..." attributes.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            // anything not listed below: only from our own server
            "default-src 'self'",
            // scripts: ours, the libraries from the two CDNs (QR code, QR scanner, charts), and Razorpay Checkout
            "script-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net https://checkout.razorpay.com"
                    + " https://*.razorpay.com",
            // styles: ours, plus style="..." set by our scripts; pictures: also QR codes drawn in the browser
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob: https:",
            "font-src 'self'",
            // the browser talks only to our API and to Razorpay (its Checkout window is a frame)
            "connect-src 'self' https://*.razorpay.com",
            "frame-src https://*.razorpay.com",
            // the QR scanner's camera video and its helper worker
            "media-src 'self' blob:",
            "worker-src 'self' blob:",
            // no plugins, no changing the base address, forms only to us or Razorpay, and no other site may
            // show our pages inside a frame (clickjacking)
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self' https://*.razorpay.com",
            "frame-ancestors 'none'");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, UserRepository userRepository)
            throws Exception {
        http
                // Stateless JWT API: no sessions, no cookies, so no CSRF token is needed.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Besides Spring's defaults (HSTS, nosniff, X-Frame-Options DENY): other sites get only our origin in
                // the Referer header, and pages may use the camera (QR scanner) but not the microphone or location.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(self), microphone=(), geolocation=(), usb=(), interest-cohort=()")))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/**", "/api/ping", "/api/centres/**", "/api/crops", "/api/queue/**", "/api/qr/**")
                        .permitAll()
                        .requestMatchers("/api/farmers/**", "/api/bookings/**", "/api/notifications/**").hasRole("FARMER")
                        .requestMatchers("/api/operator/**").hasRole("OPERATOR")
                        // what an admin may see inside these is narrowed to their centre in the controllers (AccessScope)
                        .requestMatchers("/api/admin/**", "/api/payments/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())

                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, 401, "UNAUTHORIZED", "Please log in to continue."))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, 403, "FORBIDDEN", "You do not have access to this.")))

                .addFilterBefore(new JwtAuthFilter(jwtService, userRepository), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":" + status + ",\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
