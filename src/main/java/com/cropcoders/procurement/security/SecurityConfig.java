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

import jakarta.servlet.http.HttpServletResponse;

/**
 * Who can call which API.
 * HTML/CSS/JS files are public; each page checks the login in the browser,
 * and the APIs behind it are protected here by role.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                // Stateless JWT API: no sessions, no cookies, so no CSRF token is needed.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/**", "/api/ping", "/api/centres/**", "/api/crops", "/api/queue/**")
                        .permitAll()
                        .requestMatchers("/api/farmers/**", "/api/bookings/**", "/api/notifications/**").hasRole("FARMER")
                        .requestMatchers("/api/operator/**").hasRole("OPERATOR")
                        .requestMatchers("/api/admin/**", "/api/payments/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())

                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, 401, "UNAUTHORIZED", "Please log in to continue."))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, 403, "FORBIDDEN", "You do not have access to this.")))

                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

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
