package com.cropcoders.procurement.dto;

import com.cropcoders.procurement.entity.Centre;
import com.cropcoders.procurement.entity.User;
import com.cropcoders.procurement.enums.Role;
import com.fasterxml.jackson.annotation.JsonInclude;

/** The logged-in user as the browser sees it. Staff pages are English only. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        Long id,
        String name,
        Role role,
        String email,
        String mobile,
        String username,
        Long centreId,
        String centreName,
        String language) {

    public static UserResponse farmer(User user, String language) {
        return new UserResponse(user.getId(), user.getName(), user.getRole(), user.getEmail(), user.getMobile(),
                null, null, null, language);
    }

    public static UserResponse staff(User user) {
        Centre centre = user.getCentre();
        return new UserResponse(user.getId(), user.getName(), user.getRole(), null, null, user.getUsername(),
                centre == null ? null : centre.getId(),
                centre == null ? null : centre.getName(),
                "en");
    }
}
