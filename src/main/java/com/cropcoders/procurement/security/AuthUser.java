package com.cropcoders.procurement.security;

import com.cropcoders.procurement.entity.User;
import com.cropcoders.procurement.enums.Role;

/**
 * The logged-in user: the account the JWT belongs to, read from the database on every request (JwtAuthFilter).
 * Controllers get it with {@code @AuthenticationPrincipal AuthUser user}.
 */
public record AuthUser(Long id, Role role, String name, Long centreId) {

    public static AuthUser from(User user) {
        Long centreId = user.getCentre() == null ? null : user.getCentre().getId();
        return new AuthUser(user.getId(), user.getRole(), user.getName(), centreId);
    }
}
