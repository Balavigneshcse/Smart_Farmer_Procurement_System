package com.cropcoders.procurement.security;

import org.springframework.http.HttpStatus;

import com.cropcoders.procurement.enums.Role;
import com.cropcoders.procurement.exception.ApiException;

/**
 * Which centres a logged-in admin may see and change.
 * The super admin works with every centre; a centre admin only with their own.
 */
public final class AccessScope {

    private AccessScope() {
    }

    public static boolean isSuperAdmin(AuthUser user) {
        return user.role() == Role.SUPER_ADMIN;
    }

    /** The centre to filter by: null means "all centres" (super admin). */
    public static Long centreOf(AuthUser user) {
        return isSuperAdmin(user) ? null : user.centreId();
    }

    /** Stops a centre admin from touching another centre. */
    public static void requireCentre(AuthUser user, Long centreId) {
        if (!isSuperAdmin(user) && (centreId == null || !centreId.equals(user.centreId()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OTHER_CENTRE", "This belongs to another centre.");
        }
    }

    public static void requireSuperAdmin(AuthUser user) {
        if (!isSuperAdmin(user)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SUPER_ADMIN_ONLY", "Only the super admin can do this.");
        }
    }
}
