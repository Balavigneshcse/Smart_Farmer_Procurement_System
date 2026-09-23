package com.cropcoders.procurement.exception;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** JSON body of every error response. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        int status,
        String code,
        String message,
        Map<String, Object> params,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, Map.of(), Map.of());
    }
}
