package com.cropcoders.procurement.dto;

import com.cropcoders.procurement.enums.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A new admin or staff account. A centre admin can only add staff (OPERATOR) for their own centre,
 * so their role and centreId are ignored; the super admin chooses both.
 */
public record StaffRequest(
        @NotBlank(message = "Enter the name") @Size(max = 100) String name,
        @NotBlank(message = "Enter a username")
        @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
                message = "Username: 3 to 50 letters, digits, dots, dashes or underscores")
        String username,
        @NotNull(message = "Enter a password")
        @Size(min = 8, max = 72, message = "The password needs at least 8 characters") String password,
        Role role,
        Long centreId) {
}
