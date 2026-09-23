package com.cropcoders.procurement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * registered = true  -> token + user: the farmer is logged in.
 * registered = false -> registrationToken: a new farmer must fill the registration form.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerifyOtpResponse(boolean registered, String token, UserResponse user, String registrationToken) {

    public static VerifyOtpResponse loggedIn(String token, UserResponse user) {
        return new VerifyOtpResponse(true, token, user, null);
    }

    public static VerifyOtpResponse needsRegistration(String registrationToken) {
        return new VerifyOtpResponse(false, null, null, registrationToken);
    }
}
