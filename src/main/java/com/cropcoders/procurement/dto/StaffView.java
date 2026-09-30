package com.cropcoders.procurement.dto;

import java.time.LocalDateTime;

import com.cropcoders.procurement.entity.Centre;
import com.cropcoders.procurement.entity.User;
import com.cropcoders.procurement.enums.Role;

/** An admin or staff account in the "Admins and staff" list. The password is never sent. */
public record StaffView(Long id, String name, String username, Role role, Long centreId, String centreCode,
                        String centreName, boolean active, LocalDateTime createdAt) {

    public static StaffView from(User user) {
        Centre centre = user.getCentre();
        return new StaffView(user.getId(), user.getName(), user.getUsername(), user.getRole(),
                centre == null ? null : centre.getId(),
                centre == null ? null : centre.getCode(),
                centre == null ? null : centre.getName(),
                user.isActive(), user.getCreatedAt());
    }
}
