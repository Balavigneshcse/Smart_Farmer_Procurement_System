package com.cropcoders.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Changes to an admin or staff account. The password is only changed when a new one is given. */
public record StaffUpdateRequest(
        @NotBlank(message = "Enter the name") @Size(max = 100) String name,
        boolean active,
        @Size(min = 8, max = 72, message = "The password needs at least 8 characters") String password) {
}
