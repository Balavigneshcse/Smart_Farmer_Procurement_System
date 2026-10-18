package com.cropcoders.procurement.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cropcoders.procurement.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs before every request: if the request carries a valid "Bearer" token,
 * the user is marked as logged in with their role (ROLE_FARMER, ROLE_OPERATOR, ROLE_ADMIN).
 *
 * The token alone is not enough: the account must still be switched on. A token stays valid for up to
 * 12 hours (staff) or 7 days (farmers), so without this check a switched-off account would keep working
 * until its token ran out. It costs one small query (by primary key) per logged-in request.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            jwtService.parseLoginToken(header.substring(BEARER.length()))
                    .filter(user -> userRepository.findActiveById(user.id()).orElse(false))
                    .ifPresent(user -> {
                        var authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
                        var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }
        chain.doFilter(request, response);
    }
}
